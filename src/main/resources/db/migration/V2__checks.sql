-- A check is one monitored job. The table is called "checks" because CHECK is a reserved word in SQL.

CREATE TABLE checks (
    id                bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         uuid        NOT NULL UNIQUE,
    organization_id   bigint      NOT NULL REFERENCES organizations (id),

    -- The secret in the ping URL. Kept separate from public_id: anyone who knows ping_uuid can
    -- report on the job, so it is a capability, not an identifier, and can be rotated on its own.
    ping_uuid         uuid        NOT NULL UNIQUE,

    name              text        NOT NULL CHECK (length(name) BETWEEN 1 AND 100),
    slug              text        NOT NULL CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    tags              text[]      NOT NULL DEFAULT '{}',

    schedule_type     text        NOT NULL CHECK (schedule_type IN ('SIMPLE', 'CRON')),
    period_seconds    integer     CHECK (period_seconds > 0),
    cron_expression   text        CHECK (length(cron_expression) <= 100),
    -- IANA zone id, e.g. Asia/Kolkata. Cron expressions are evaluated in this zone; all stored instants are UTC.
    time_zone         text        NOT NULL DEFAULT 'UTC',
    grace_seconds     integer     NOT NULL CHECK (grace_seconds >= 0),

    -- Only CheckStateMachine (Phase 2) changes status.
    status            text        NOT NULL DEFAULT 'NEW'
                                  CHECK (status IN ('NEW', 'UP', 'LATE', 'DOWN', 'PAUSED')),
    last_ping_at      timestamptz,
    last_start_at     timestamptz,
    next_expected_at  timestamptz,
    paused_at         timestamptz,

    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    -- Optimistic locking: two concurrent edits can't silently overwrite each other (exposed as ETag in the API).
    version           bigint      NOT NULL DEFAULT 0,

    -- The database enforces that a schedule is either a period or a cron expression, never both or neither.
    CONSTRAINT checks_schedule_consistent CHECK (
        (schedule_type = 'SIMPLE' AND period_seconds IS NOT NULL AND cron_expression IS NULL)
        OR
        (schedule_type = 'CRON' AND cron_expression IS NOT NULL AND period_seconds IS NULL)
    ),
    -- Slugs are unique within an organisation, not globally: two tenants can both have "nightly-backup".
    CONSTRAINT checks_org_slug_uq UNIQUE (organization_id, slug)
);

-- The detector's query (Phase 2): "UP or LATE checks whose next ping is overdue".
-- A partial index only contains rows the detector can act on; NEW, DOWN and PAUSED checks
-- never enter it, so it stays small no matter how many checks exist.
CREATE INDEX checks_due_idx ON checks (next_expected_at) WHERE status IN ('UP', 'LATE');
