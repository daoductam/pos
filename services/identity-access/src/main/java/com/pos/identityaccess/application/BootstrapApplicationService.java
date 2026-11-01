package com.pos.identityaccess.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.identityaccess.api.dto.BootstrapTenantRequest;
import com.pos.identityaccess.api.dto.MembershipResponse;
import com.pos.identityaccess.api.dto.TenantBootstrapResponse;
import com.pos.identityaccess.application.exception.ForbiddenException;
import com.pos.identityaccess.application.exception.IdempotencyConflictException;
import com.pos.identityaccess.application.exception.ValidationException;
import com.pos.identityaccess.application.model.IdempotencyCommand;
import com.pos.identityaccess.application.model.IdempotencyKey;
import com.pos.identityaccess.application.model.IdempotencyRecord;
import com.pos.identityaccess.application.model.InvitationDelivery;
import com.pos.identityaccess.application.model.OutboxEvent;
import com.pos.identityaccess.application.port.IdempotencyStore;
import com.pos.identityaccess.application.port.InvitationDeliveryStore;
import com.pos.identityaccess.application.port.MembershipRepository;
import com.pos.identityaccess.application.port.OutboxStore;
import com.pos.identityaccess.application.port.TenantRepository;
import com.pos.identityaccess.application.port.UserRepository;
import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.Role;
import com.pos.identityaccess.domain.model.Tenant;
import com.pos.identityaccess.domain.model.TenantStatus;
import com.pos.identityaccess.domain.model.User;
import com.pos.identityaccess.domain.model.UserStatus;
import com.pos.identityaccess.infrastructure.persistence.TransactionRunner;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

@Service
public final class BootstrapApplicationService {

    public static final String PLATFORM_BOOTSTRAP_AUTHORITY = "ROLE_PLATFORM_BOOTSTRAP";

    private final TenantRepository tenants;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final IdempotencyStore idempotency;
    private final OutboxStore outbox;
    private final InvitationDeliveryStore deliveries;
    private final TransactionRunner transactions;
    private final ObjectMapper objectMapper;

    public BootstrapApplicationService(
            TenantRepository tenants,
            UserRepository users,
            MembershipRepository memberships,
            IdempotencyStore idempotency,
            OutboxStore outbox,
            InvitationDeliveryStore deliveries,
            TransactionRunner transactions,
            ObjectMapper objectMapper) {
        this.tenants = tenants;
        this.users = users;
        this.memberships = memberships;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.deliveries = deliveries;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
    }

    public TenantBootstrapResponse bootstrap(
            BootstrapTenantRequest request,
            Authentication authentication,
            IdempotencyCommand command) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> PLATFORM_BOOTSTRAP_AUTHORITY.equals(authority.getAuthority()))) {
            throw new ForbiddenException();
        }
        if (request == null) {
            throw new ValidationException("bootstrap request is required");
        }
        String name = required(request.name(), "name", 200);
        String email = normalizeEmail(request.ownerEmail());
        String ownerName = required(request.ownerDisplayName(), "ownerDisplayName", 200);
        String actorSubject = Objects.requireNonNull(authentication.getName(), "authentication.name");
        IdempotencyKey key = new IdempotencyKey(null, actorSubject, command.key(), command.requestHash());
        var previous = idempotency.find(key);
        if (previous.isPresent()) {
            if (!previous.get().key().requestHash().equals(command.requestHash())) {
                throw new IdempotencyConflictException();
            }
            return read(previous.get().responseMetadata());
        }

        return transactions.inTransaction(() -> {
            var concurrent = idempotency.find(key);
            if (concurrent.isPresent()) {
                if (!concurrent.get().key().requestHash().equals(command.requestHash())) {
                    throw new IdempotencyConflictException();
                }
                return read(concurrent.get().responseMetadata());
            }
            Instant now = Instant.now();
            Tenant tenant = new Tenant(IdGenerator.uuidV7(), name, TenantStatus.ACTIVE, 1L,
                    now, now, actorSubject, actorSubject);
            User owner = new User(IdGenerator.uuidV7(), null, email, ownerName, null,
                    UserStatus.ACTIVE, "PENDING", now, now);
            Membership membership = new Membership(IdGenerator.uuidV7(), tenant.id(), owner.id(),
                    null, Role.OWNER, MembershipStatus.INVITED, 1L, actorSubject, actorSubject);
            tenants.save(tenant);
            users.save(owner);
            memberships.save(membership);
            java.util.UUID deliveryId = IdGenerator.uuidV7();
            deliveries.save(InvitationDelivery.queued(deliveryId, membership.id()));
            outbox.save(new OutboxEvent(IdGenerator.uuidV7(), tenant.id(), null,
                    "InvitationRequested", 1, "Membership", membership.id(),
                    "{\"membershipId\":\"" + membership.id() + "\",\"tenantId\":\""
                            + tenant.id() + "\",\"deliveryId\":\"" + deliveryId + "\"}",
                    actorSubject, actorSubject, now));
            TenantBootstrapResponse response = new TenantBootstrapResponse(ResponseMapper.tenant(tenant),
                    ResponseMapper.membership(membership, owner, "QUEUED"));
            idempotency.save(new IdempotencyRecord(key, 201, write(response),
                    now.plus(24, ChronoUnit.HOURS)));
            return response;
        });
    }

    private TenantBootstrapResponse read(String json) {
        try {
            return objectMapper.readValue(json, TenantBootstrapResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("stored bootstrap response is invalid", exception);
        }
    }

    private String write(TenantBootstrapResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("bootstrap response cannot be serialized", exception);
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank() || email.length() > 320 || !email.contains("@")) {
            throw new ValidationException("ownerEmail is invalid");
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
