package com.pos.identityaccess.application;

import com.pos.identityaccess.api.dto.BranchResponse;
import com.pos.identityaccess.api.dto.CreateBranchRequest;
import com.pos.identityaccess.api.dto.UpdateBranchRequest;
import com.pos.identityaccess.application.exception.ConflictException;
import com.pos.identityaccess.application.exception.ForbiddenException;
import com.pos.identityaccess.application.exception.NotFoundInScopeException;
import com.pos.identityaccess.application.exception.OptimisticConflictException;
import com.pos.identityaccess.application.exception.ValidationException;
import com.pos.identityaccess.application.model.ActorContext;
import com.pos.identityaccess.application.model.AuthorizationScope;
import com.pos.identityaccess.application.model.OutboxEvent;
import com.pos.identityaccess.application.port.BranchRepository;
import com.pos.identityaccess.application.port.OutboxStore;
import com.pos.identityaccess.domain.model.Branch;
import com.pos.identityaccess.domain.model.BranchStatus;
import com.pos.identityaccess.domain.model.Role;
import com.pos.identityaccess.infrastructure.persistence.TransactionRunner;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class BranchApplicationService {

    private final BranchRepository branches;
    private final OutboxStore outbox;
    private final TransactionRunner transactions;

    public BranchApplicationService(
            BranchRepository branches,
            OutboxStore outbox,
            TransactionRunner transactions) {
        this.branches = Objects.requireNonNull(branches, "branches");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public List<BranchResponse> list(UUID tenantId, ActorContext actor) {
        requireTenant(actor, tenantId);
        AuthorizationScope scope = scope(actor);
        return branches.findVisibleByTenant(scope, tenantId).stream()
                .map(ResponseMapper::branch)
                .toList();
    }

    public BranchResponse create(UUID tenantId, CreateBranchRequest request, ActorContext actor) {
        requireOwner(actor, tenantId);
        if (request == null) {
            throw new ValidationException("branch request is required");
        }
        String code = normalizeCode(request.code());
        String name = required(request.name(), "name", 200);
        if (request.address() != null && request.address().length() > 500) {
            throw new ValidationException("address must not exceed 500 characters");
        }
        Instant now = Instant.now();
        Branch branch = new Branch(IdGenerator.uuidV7(), tenantId, request.code().trim(), code, name,
                request.address(), BranchStatus.ACTIVE, 1L, now, now, actor.subject(), actor.subject());
        return transactions.inTransaction(() -> {
            try {
                Branch saved = branches.save(branch);
                outbox.save(event("BranchCreated", saved, actor.subject()));
                return ResponseMapper.branch(saved);
            } catch (org.springframework.dao.DuplicateKeyException exception) {
                throw new ConflictException("conflict", "Branch code already exists");
            }
        });
    }

    public BranchResponse update(
            UUID tenantId,
            UUID branchId,
            UpdateBranchRequest request,
            long expectedVersion,
            ActorContext actor) {
        requireOwner(actor, tenantId);
        Branch current = find(tenantId, branchId, actor);
        if (request == null) {
            throw new ValidationException("branch request is required");
        }
        String name = required(request.name(), "name", 200);
        if (request.address() != null && request.address().length() > 500) {
            throw new ValidationException("address must not exceed 500 characters");
        }
        Branch updated = new Branch(current.id(), current.tenantId(), current.code(),
                current.normalizedCode(), name, request.address(), current.status(), current.version(),
                current.createdAt(), Instant.now(), current.createdBy(), actor.subject());
        return transactions.inTransaction(() -> {
            if (!branches.updateIfVersionMatches(updated, expectedVersion)) {
                throw new OptimisticConflictException();
            }
            Branch result = withVersion(updated, expectedVersion + 1);
            outbox.save(event("BranchUpdated", result, actor.subject()));
            return ResponseMapper.branch(result);
        });
    }

    public BranchResponse archive(
            UUID tenantId,
            UUID branchId,
            String reason,
            long expectedVersion,
            ActorContext actor) {
        requireOwner(actor, tenantId);
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ValidationException("archive reason must contain 1 to 500 characters");
        }
        Branch current = find(tenantId, branchId, actor);
        Branch updated = new Branch(current.id(), current.tenantId(), current.code(),
                current.normalizedCode(), current.name(), current.address(), BranchStatus.ARCHIVED,
                current.version(), current.createdAt(), Instant.now(), current.createdBy(), actor.subject());
        return transactions.inTransaction(() -> {
            if (!branches.updateIfVersionMatches(updated, expectedVersion)) {
                throw new OptimisticConflictException();
            }
            Branch result = withVersion(updated, expectedVersion + 1);
            outbox.save(event("BranchArchived", result, actor.subject()));
            return ResponseMapper.branch(result);
        });
    }

    private Branch find(UUID tenantId, UUID branchId, ActorContext actor) {
        return branches.findVisibleById(scope(actor), tenantId, branchId)
                .orElseThrow(NotFoundInScopeException::new);
    }

    private static Branch withVersion(Branch branch, long version) {
        return new Branch(branch.id(), branch.tenantId(), branch.code(), branch.normalizedCode(),
                branch.name(), branch.address(), branch.status(), version, branch.createdAt(),
                branch.updatedAt(), branch.createdBy(), branch.updatedBy());
    }

    private static OutboxEvent event(String type, Branch branch, String actor) {
        String payload = "{\"branchId\":\"" + branch.id() + "\",\"tenantId\":\""
                + branch.tenantId() + "\",\"status\":\"" + branch.status() + "\"}";
        return new OutboxEvent(IdGenerator.uuidV7(), branch.tenantId(), branch.id(), type, 1,
                "Branch", branch.id(), payload, actor, actor, Instant.now());
    }

    private static AuthorizationScope scope(ActorContext actor) {
        return new AuthorizationScope(actor.subject(), actor.tenantId(), actor.role(), actor.branchIds());
    }

    private static void requireOwner(ActorContext actor, UUID tenantId) {
        requireTenant(actor, tenantId);
        if (actor.role() != Role.OWNER) {
            throw new ForbiddenException();
        }
    }

    private static void requireTenant(ActorContext actor, UUID tenantId) {
        if (actor == null || tenantId == null || !tenantId.equals(actor.tenantId())) {
            throw new NotFoundInScopeException();
        }
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank() || code.length() > 100) {
            throw new ValidationException("code must contain 1 to 100 characters");
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new ValidationException(field + " must contain 1 to " + max + " characters");
        }
        return value.trim();
    }
}
