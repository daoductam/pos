package com.pos.identityaccess.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.identityaccess.api.dto.InvitationDeliveryResponse;
import com.pos.identityaccess.api.dto.EmployeeResponse;
import com.pos.identityaccess.api.dto.InviteMembershipRequest;
import com.pos.identityaccess.api.dto.MembershipResponse;
import com.pos.identityaccess.api.dto.UpdateMembershipRequest;
import com.pos.identityaccess.application.exception.ConflictException;
import com.pos.identityaccess.application.exception.ForbiddenException;
import com.pos.identityaccess.application.exception.IdempotencyConflictException;
import com.pos.identityaccess.application.exception.LastOwnerProtectedException;
import com.pos.identityaccess.application.exception.NotFoundInScopeException;
import com.pos.identityaccess.application.exception.OptimisticConflictException;
import com.pos.identityaccess.application.exception.ValidationException;
import com.pos.identityaccess.application.model.ActorContext;
import com.pos.identityaccess.application.model.AuthorizationScope;
import com.pos.identityaccess.application.model.IdempotencyCommand;
import com.pos.identityaccess.application.model.IdempotencyKey;
import com.pos.identityaccess.application.model.IdempotencyRecord;
import com.pos.identityaccess.application.model.InvitationDelivery;
import com.pos.identityaccess.application.model.OutboxEvent;
import com.pos.identityaccess.application.port.BranchRepository;
import com.pos.identityaccess.application.port.IdempotencyStore;
import com.pos.identityaccess.application.port.InvitationDeliveryStore;
import com.pos.identityaccess.application.port.MembershipRepository;
import com.pos.identityaccess.application.port.OutboxStore;
import com.pos.identityaccess.application.port.UserRepository;
import com.pos.identityaccess.domain.model.BranchStatus;
import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.Role;
import com.pos.identityaccess.domain.model.User;
import com.pos.identityaccess.domain.model.UserStatus;
import com.pos.identityaccess.domain.policy.AuthorizationPolicy;
import com.pos.identityaccess.domain.policy.MembershipLifecycle;
import com.pos.identityaccess.domain.policy.OwnerInvariant;
import com.pos.identityaccess.infrastructure.persistence.TransactionRunner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public final class MembershipApplicationService {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final BranchRepository branches;
    private final IdempotencyStore idempotency;
    private final OutboxStore outbox;
    private final InvitationDeliveryStore deliveries;
    private final TransactionRunner transactions;
    private final ObjectMapper objectMapper;

    public MembershipApplicationService(
            UserRepository users,
            MembershipRepository memberships,
            BranchRepository branches,
            IdempotencyStore idempotency,
            OutboxStore outbox,
            InvitationDeliveryStore deliveries,
            TransactionRunner transactions,
            ObjectMapper objectMapper) {
        this.users = Objects.requireNonNull(users, "users");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.branches = Objects.requireNonNull(branches, "branches");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public MembershipResponse invite(
            UUID tenantId,
            InviteMembershipRequest request,
            ActorContext actor,
            IdempotencyCommand command) {
        requireTenant(actor, tenantId);
        if (request == null || request.role() == null) {
            throw new ValidationException("role and invitation data are required");
        }
        String email = normalizeEmail(request.email());
        String displayName = required(request.displayName(), "displayName", 200);
        validateBranch(tenantId, request.role(), request.branchId(), actor);
        return executeIdempotent(tenantId, actor, command, MembershipResponse.class, () -> {
            User user = users.findByNormalizedEmail(email).orElseGet(() -> new User(
                    IdGenerator.uuidV7(), null, email, displayName, request.phone(), UserStatus.ACTIVE,
                    "PENDING", Instant.now(), Instant.now()));
            assertNoCurrentMembership(tenantId, user.id(), request.role(), request.branchId());
            Membership membership = new Membership(IdGenerator.uuidV7(), tenantId, user.id(),
                    request.branchId(), request.role(), MembershipStatus.INVITED, 1L,
                    actor.subject(), actor.subject());
            UUID deliveryId = IdGenerator.uuidV7();
            return transactions.inTransaction(() -> {
                if (users.findByNormalizedEmail(email).isEmpty()) {
                    users.save(user);
                }
                memberships.save(membership);
                deliveries.save(InvitationDelivery.queued(deliveryId, membership.id()));
                outbox.save(invitationEvent(membership, deliveryId, actor.subject()));
                return ResponseMapper.membership(membership, user, "QUEUED");
            });
        });
    }

    public MembershipResponse update(
            UUID tenantId,
            UUID membershipId,
            UpdateMembershipRequest request,
            long expectedVersion,
            ActorContext actor,
            IdempotencyCommand command) {
        requireTenant(actor, tenantId);
        if (request == null || request.role() == null) {
            throw new ValidationException("role is required");
        }
        validateBranch(tenantId, request.role(), request.branchId(), actor);
        return executeIdempotent(tenantId, actor, command, MembershipResponse.class, () -> {
            Membership current = findVisible(tenantId, membershipId, actor);
            assertCanManage(actor, current);
            if (!OwnerInvariant.canRemoveActiveOwner(current.role(), current.status(),
                    memberships.countActiveOwners(tenantId))) {
                throw new LastOwnerProtectedException();
            }
            User user = users.findById(current.userId()).orElse(null);
            Membership updated = new Membership(current.id(), current.tenantId(), current.userId(),
                    request.branchId(), request.role(), current.status(), current.version(),
                    current.createdBy(), actor.subject(), current.statusReason());
            return transactions.inTransaction(() -> {
                if (!memberships.updateIfVersionMatches(updated, expectedVersion)) {
                    throw new OptimisticConflictException();
                }
                Membership result = withVersion(updated, expectedVersion + 1);
                outbox.save(event("MembershipChanged", result, actor.subject()));
                return ResponseMapper.membership(result, user, null);
            });
        });
    }

    public MembershipResponse get(UUID tenantId, UUID membershipId, ActorContext actor) {
        requireTenant(actor, tenantId);
        Membership membership = findVisible(tenantId, membershipId, actor);
        User user = users.findById(membership.userId()).orElse(null);
        return ResponseMapper.membership(membership, user, null);
    }

    public List<MembershipResponse> list(UUID tenantId, ActorContext actor) {
        requireTenant(actor, tenantId);
        return memberships.findVisibleByTenant(scope(actor), tenantId).stream()
                .map(membership -> ResponseMapper.membership(membership,
                        users.findById(membership.userId()).orElse(null), null))
                .toList();
    }

    public List<EmployeeResponse> listEmployees(UUID tenantId, ActorContext actor) {
        requireTenant(actor, tenantId);
        Map<UUID, List<Membership>> grouped = memberships.findVisibleByTenant(scope(actor), tenantId)
                .stream().collect(Collectors.groupingBy(Membership::userId));
        return grouped.entrySet().stream()
                .map(entry -> employee(entry.getKey(), entry.getValue()))
                .toList();
    }

    public EmployeeResponse getEmployee(UUID tenantId, UUID userId, ActorContext actor) {
        requireTenant(actor, tenantId);
        List<Membership> visible = memberships.findVisibleByTenant(scope(actor), tenantId).stream()
                .filter(membership -> membership.userId().equals(userId))
                .toList();
        if (visible.isEmpty()) {
            throw new NotFoundInScopeException();
        }
        return employee(userId, visible);
    }

    private EmployeeResponse employee(UUID userId, List<Membership> memberships) {
        User user = users.findById(userId).orElseThrow(NotFoundInScopeException::new);
        return new EmployeeResponse(user.id(), user.normalizedEmail(), user.displayName(), user.phone(),
                user.status(), memberships.stream()
                        .map(membership -> ResponseMapper.membership(membership, user, null))
                        .toList());
    }

    public MembershipResponse changeStatus(
            UUID tenantId,
            UUID membershipId,
            MembershipStatus next,
            String reason,
            long expectedVersion,
            ActorContext actor,
            IdempotencyCommand command) {
        requireTenant(actor, tenantId);
        if (next == null || reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ValidationException("status and reason are required");
        }
        return executeIdempotent(tenantId, actor, command, MembershipResponse.class, () -> {
            Membership current = findVisible(tenantId, membershipId, actor);
            assertCanManage(actor, current);
            MembershipLifecycle.transition(current.status(), next);
            if (!OwnerInvariant.canRemoveActiveOwner(current.role(), current.status(),
                    memberships.countActiveOwners(tenantId))) {
                throw new LastOwnerProtectedException();
            }
            User user = users.findById(current.userId()).orElse(null);
            Membership updated = new Membership(current.id(), current.tenantId(), current.userId(),
                    current.branchId(), current.role(), next, current.version(), current.createdBy(),
                    actor.subject(), reason);
            return transactions.inTransaction(() -> {
                if (!memberships.updateIfVersionMatches(updated, expectedVersion)) {
                    throw new OptimisticConflictException();
                }
                Membership result = withVersion(updated, expectedVersion + 1);
                String eventType = switch (next) {
                    case ACTIVE -> "MembershipActivated";
                    case SUSPENDED -> "MembershipSuspended";
                    case REVOKED -> "MembershipRevoked";
                    case INVITED -> "MembershipChanged";
                };
                outbox.save(event(eventType, result, actor.subject()));
                return ResponseMapper.membership(result, user, null);
            });
        });
    }

    public InvitationDeliveryResponse resendInvitation(
            UUID tenantId,
            UUID membershipId,
            ActorContext actor,
            IdempotencyCommand command) {
        requireTenant(actor, tenantId);
        return executeIdempotent(tenantId, actor, command, InvitationDeliveryResponse.class, () -> {
            Membership membership = findVisible(tenantId, membershipId, actor);
            assertCanManage(actor, membership);
            if (membership.status() != MembershipStatus.INVITED) {
                throw new ConflictException("membership_state_conflict", "Only invited memberships can be resent");
            }
            UUID deliveryId = IdGenerator.uuidV7();
            deliveries.save(InvitationDelivery.queued(deliveryId, membership.id()));
            outbox.save(new OutboxEvent(deliveryId, tenantId, membership.branchId(),
                    "InvitationRequested", 1, "Membership", membership.id(),
                    "{\"membershipId\":\"" + membership.id() + "\",\"deliveryId\":\""
                            + deliveryId + "\"}", actor.subject(), actor.subject(), Instant.now()));
            return new InvitationDeliveryResponse(deliveryId, membership.id(), "QUEUED", 0, null, null);
        });
    }

    private <T> T executeIdempotent(
            UUID tenantId,
            ActorContext actor,
            IdempotencyCommand command,
            Class<T> responseType,
            Supplier<T> mutation) {
        Objects.requireNonNull(command, "idempotency command");
        IdempotencyKey key = new IdempotencyKey(tenantId, actor.subject(), command.key(), command.requestHash());
        var previous = idempotency.find(key);
        if (previous.isPresent()) {
            if (!previous.get().key().requestHash().equals(command.requestHash())) {
                throw new IdempotencyConflictException();
            }
            return read(previous.get().responseMetadata(), responseType);
        }
        return transactions.inTransaction(() -> {
            var concurrent = idempotency.find(key);
            if (concurrent.isPresent()) {
                if (!concurrent.get().key().requestHash().equals(command.requestHash())) {
                    throw new IdempotencyConflictException();
                }
                return read(concurrent.get().responseMetadata(), responseType);
            }
            T response = mutation.get();
            idempotency.save(new IdempotencyRecord(key, responseType == InvitationDeliveryResponse.class ? 202 : 201,
                    write(response), Instant.now().plus(24, ChronoUnit.HOURS)));
            return response;
        });
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("stored idempotency response is invalid", exception);
        }
    }

    private String write(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("idempotency response cannot be serialized", exception);
        }
    }

    private Membership findVisible(UUID tenantId, UUID membershipId, ActorContext actor) {
        return memberships.findVisibleById(scope(actor), tenantId, membershipId)
                .orElseThrow(NotFoundInScopeException::new);
    }

    private void assertCanManage(ActorContext actor, Membership target) {
        if (!AuthorizationPolicy.canManageMembership(actor, target.role(), target.branchId())) {
            throw new ForbiddenException();
        }
    }

    private void assertNoCurrentMembership(UUID tenantId, UUID userId, Role role, UUID branchId) {
        var current = role == Role.OWNER
                ? memberships.findCurrentTenantOwner(tenantId, userId)
                : memberships.findCurrentByUserAndBranch(tenantId, userId, branchId);
        if (current.isPresent()) {
            throw new ConflictException("invitation_pending", "An active membership already exists");
        }
    }

    private void validateBranch(UUID tenantId, Role role, UUID branchId, ActorContext actor) {
        if (role.requiresBranch() != (branchId != null)) {
            throw new ValidationException("role and branch scope do not match");
        }
        if (branchId == null) {
            if (!AuthorizationPolicy.canManageMembership(actor, role, null)) {
                throw new ForbiddenException();
            }
            return;
        }
        var branch = branches.findVisibleById(scope(actor), tenantId, branchId)
                .orElseThrow(ForbiddenException::new);
        if (branch.status() != BranchStatus.ACTIVE) {
            throw new ForbiddenException();
        }
        if (!AuthorizationPolicy.canManageMembership(actor, role, branchId)) {
            throw new ForbiddenException();
        }
    }

    private static Membership withVersion(Membership membership, long version) {
        return new Membership(membership.id(), membership.tenantId(), membership.userId(),
                membership.branchId(), membership.role(), membership.status(), version,
                membership.createdBy(), membership.updatedBy(), membership.statusReason());
    }

    private static OutboxEvent event(String type, Membership membership, String actor) {
        String payload = "{\"membershipId\":\"" + membership.id() + "\",\"tenantId\":\""
                + membership.tenantId() + "\",\"status\":\"" + membership.status() + "\"}";
        return new OutboxEvent(IdGenerator.uuidV7(), membership.tenantId(), membership.branchId(),
                type, 1, "Membership", membership.id(), payload, actor, actor, Instant.now());
    }

    private static OutboxEvent invitationEvent(Membership membership, UUID deliveryId, String actor) {
        String payload = "{\"membershipId\":\"" + membership.id() + "\",\"tenantId\":\""
                + membership.tenantId() + "\",\"status\":\"INVITED\",\"deliveryId\":\""
                + deliveryId + "\"}";
        return new OutboxEvent(IdGenerator.uuidV7(), membership.tenantId(), membership.branchId(),
                "InvitationRequested", 1, "Membership", membership.id(), payload, actor, actor, Instant.now());
    }

    private static AuthorizationScope scope(ActorContext actor) {
        return new AuthorizationScope(actor.subject(), actor.tenantId(), actor.role(), actor.branchIds());
    }

    private static void requireTenant(ActorContext actor, UUID tenantId) {
        if (actor == null || tenantId == null || !tenantId.equals(actor.tenantId())) {
            throw new NotFoundInScopeException();
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank() || email.length() > 320 || !email.contains("@")) {
            throw new ValidationException("email is invalid");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new ValidationException(field + " must contain 1 to " + max + " characters");
        }
        return value.trim();
    }
}
