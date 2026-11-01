CREATE TABLE memberships (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES users(id),
    branch_id UUID,
    role TEXT NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'CASHIER', 'KITCHEN')),
    status TEXT NOT NULL CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REVOKED')),
    status_reason TEXT CHECK (status_reason IS NULL OR length(status_reason) <= 500),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    invited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at TIMESTAMPTZ,
    suspended_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by TEXT NOT NULL,
    updated_by TEXT NOT NULL,
    CONSTRAINT fk_memberships_branch_tenant
        FOREIGN KEY (tenant_id, branch_id) REFERENCES branches (tenant_id, id),
    CONSTRAINT chk_memberships_role_branch
        CHECK ((role = 'OWNER' AND branch_id IS NULL)
            OR (role <> 'OWNER' AND branch_id IS NOT NULL))
);

COMMENT ON COLUMN memberships.id IS 'Membership history record identifier.';
COMMENT ON COLUMN memberships.tenant_id IS 'Tenant scope of the membership.';
COMMENT ON COLUMN memberships.user_id IS 'User receiving the membership.';
COMMENT ON COLUMN memberships.branch_id IS 'Assigned branch; null only for tenant-wide owner membership.';
COMMENT ON COLUMN memberships.role IS 'One effective role for this membership scope.';
COMMENT ON COLUMN memberships.status IS 'Membership lifecycle state.';
COMMENT ON COLUMN memberships.status_reason IS 'Safe operator-provided suspension or revocation reason.';
COMMENT ON COLUMN memberships.version IS 'If-Match optimistic concurrency version.';
COMMENT ON COLUMN memberships.invited_at IS 'Time the membership invitation was created.';
COMMENT ON COLUMN memberships.activated_at IS 'First activation time after Cognito mapping.';
COMMENT ON COLUMN memberships.suspended_at IS 'Most recent suspension timestamp.';
COMMENT ON COLUMN memberships.revoked_at IS 'Terminal revocation timestamp.';
COMMENT ON COLUMN memberships.created_at IS 'Membership history creation timestamp.';
COMMENT ON COLUMN memberships.updated_at IS 'Last membership mutation timestamp.';
COMMENT ON COLUMN memberships.created_by IS 'Opaque actor reference that created the membership.';
COMMENT ON COLUMN memberships.updated_by IS 'Opaque actor reference that last changed the membership.';

CREATE UNIQUE INDEX uq_memberships_active_tenant_user
    ON memberships (tenant_id, user_id)
    WHERE branch_id IS NULL AND status IN ('INVITED', 'ACTIVE', 'SUSPENDED');

CREATE UNIQUE INDEX uq_memberships_active_branch_user
    ON memberships (tenant_id, user_id, branch_id)
    WHERE branch_id IS NOT NULL AND status IN ('INVITED', 'ACTIVE', 'SUSPENDED');

CREATE INDEX idx_memberships_subject_status
    ON memberships (user_id, status, tenant_id, branch_id);

CREATE INDEX idx_memberships_scope_status
    ON memberships (tenant_id, branch_id, status, role, user_id);

CREATE TABLE invitation_deliveries (
    id UUID PRIMARY KEY,
    membership_id UUID NOT NULL REFERENCES memberships(id),
    provider TEXT NOT NULL CHECK (provider = 'COGNITO'),
    status TEXT NOT NULL CHECK (status IN ('QUEUED', 'SENT', 'FAILED', 'ACCEPTED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ,
    last_error_code TEXT,
    provider_user_reference TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN invitation_deliveries.id IS 'Invitation delivery attempt identifier.';
COMMENT ON COLUMN invitation_deliveries.membership_id IS 'Membership targeted by the delivery.';
COMMENT ON COLUMN invitation_deliveries.provider IS 'External invitation provider; P0 is Cognito.';
COMMENT ON COLUMN invitation_deliveries.status IS 'Delivery and reconciliation lifecycle state.';
COMMENT ON COLUMN invitation_deliveries.attempt_count IS 'Number of provider calls made.';
COMMENT ON COLUMN invitation_deliveries.next_attempt_at IS 'Next bounded retry time.';
COMMENT ON COLUMN invitation_deliveries.last_error_code IS 'Redacted machine-safe failure code.';
COMMENT ON COLUMN invitation_deliveries.provider_user_reference IS 'Opaque provider reference if available.';
COMMENT ON COLUMN invitation_deliveries.created_at IS 'Delivery record creation timestamp.';
COMMENT ON COLUMN invitation_deliveries.updated_at IS 'Last delivery state timestamp.';

CREATE INDEX idx_invitation_retry
    ON invitation_deliveries (status, next_attempt_at);
