CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(id),
    actor_subject TEXT NOT NULL,
    idempotency_key TEXT NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 200),
    request_hash TEXT NOT NULL,
    response_status INTEGER NOT NULL CHECK (response_status BETWEEN 100 AND 599),
    response_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN idempotency_records.id IS 'Idempotency record identifier.';
COMMENT ON COLUMN idempotency_records.tenant_id IS 'Tenant scope; null only for platform bootstrap.';
COMMENT ON COLUMN idempotency_records.actor_subject IS 'Opaque authenticated or workload actor scope.';
COMMENT ON COLUMN idempotency_records.idempotency_key IS 'Client retry key.';
COMMENT ON COLUMN idempotency_records.request_hash IS 'Canonical request payload digest.';
COMMENT ON COLUMN idempotency_records.response_status IS 'Original committed HTTP response status.';
COMMENT ON COLUMN idempotency_records.response_metadata IS 'Bounded replay-safe response metadata without secrets.';
COMMENT ON COLUMN idempotency_records.expires_at IS 'Retention time after which the record may be cleaned up.';
COMMENT ON COLUMN idempotency_records.created_at IS 'Record creation timestamp.';

CREATE UNIQUE INDEX uq_idempotency_actor_key
    ON idempotency_records (
        COALESCE(tenant_id, '00000000-0000-7000-8000-000000000000'::uuid),
        actor_subject,
        idempotency_key
    );

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(id),
    branch_id UUID,
    event_type TEXT NOT NULL,
    event_version INTEGER NOT NULL CHECK (event_version > 0),
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    correlation_id TEXT NOT NULL,
    causation_id TEXT,
    publish_status TEXT NOT NULL CHECK (publish_status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_error_code TEXT,
    next_attempt_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_outbox_branch_scope CHECK (branch_id IS NULL OR tenant_id IS NOT NULL),
    CONSTRAINT fk_outbox_branch_tenant
        FOREIGN KEY (tenant_id, branch_id) REFERENCES branches (tenant_id, id)
);

COMMENT ON COLUMN outbox_events.id IS 'Stable event ID used for consumer deduplication.';
COMMENT ON COLUMN outbox_events.tenant_id IS 'Tenant carried by the event; null only for platform events.';
COMMENT ON COLUMN outbox_events.branch_id IS 'Branch carried by a branch-scoped event.';
COMMENT ON COLUMN outbox_events.event_type IS 'Allowlisted versioned event type name.';
COMMENT ON COLUMN outbox_events.event_version IS 'Event schema version.';
COMMENT ON COLUMN outbox_events.aggregate_type IS 'Aggregate family that produced the event.';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'Aggregate identifier.';
COMMENT ON COLUMN outbox_events.payload IS 'Redacted event payload without secrets or raw PII.';
COMMENT ON COLUMN outbox_events.correlation_id IS 'Request or workflow correlation identifier.';
COMMENT ON COLUMN outbox_events.causation_id IS 'Parent event or command identifier.';
COMMENT ON COLUMN outbox_events.publish_status IS 'Publisher lifecycle state.';
COMMENT ON COLUMN outbox_events.attempt_count IS 'Number of broker publish attempts.';
COMMENT ON COLUMN outbox_events.last_error_code IS 'Redacted machine-safe retry or failure code.';
COMMENT ON COLUMN outbox_events.next_attempt_at IS 'Next retry time.';
COMMENT ON COLUMN outbox_events.published_at IS 'Broker acknowledgement timestamp.';
COMMENT ON COLUMN outbox_events.created_at IS 'Event creation timestamp.';

CREATE INDEX idx_outbox_publish_queue
    ON outbox_events (publish_status, next_attempt_at, created_at);

CREATE INDEX idx_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id, created_at);
