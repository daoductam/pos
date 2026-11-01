INSERT INTO tenants (id, name, status, version, created_by, updated_by)
VALUES ('00000000-0000-7000-8000-000000000001', 'Local Demo Tenant', 'ACTIVE', 1, 'local-seed', 'local-seed')
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (
    id,
    cognito_subject,
    normalized_email,
    display_name,
    status,
    reconciliation_status
)
VALUES (
    '00000000-0000-7000-8000-000000000002',
    'local-owner-sub',
    'owner@local.test',
    'Local Owner',
    'ACTIVE',
    'ALIGNED'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO memberships (
    id,
    tenant_id,
    user_id,
    branch_id,
    role,
    status,
    activated_at,
    created_by,
    updated_by
)
VALUES (
    '00000000-0000-7000-8000-000000000003',
    '00000000-0000-7000-8000-000000000001',
    '00000000-0000-7000-8000-000000000002',
    NULL,
    'OWNER',
    'ACTIVE',
    now(),
    'local-seed',
    'local-seed'
)
ON CONFLICT (id) DO NOTHING;

COMMENT ON COLUMN tenants.id IS 'Local fixture tenant seed; production data must not use this row.';
