-- Pings and status changes are the highest-volume tables. They are child rows of a check:
-- they carry check_id but not organization_id, because they are only ever read through a
-- check that has already been tenant-checked (see docs/adr/0002-tenancy-and-ids.md).

CREATE TABLE pings (
    id              bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    check_id        bigint      NOT NULL REFERENCES checks (id) ON DELETE CASCADE,
    type            text        NOT NULL CHECK (type IN ('SUCCESS', 'START', 'FAIL')),
    exit_code       integer,
    received_at     timestamptz NOT NULL,
    duration_ms     bigint      CHECK (duration_ms >= 0),
    source_ip       inet,
    user_agent      text        CHECK (length(user_agent) <= 512),
    method          text        NOT NULL CHECK (method IN ('GET', 'POST', 'HEAD')),
    -- Job output, capped at 10 KB by the ingest endpoint (Phase 2).
    body            text        CHECK (octet_length(body) <= 10240),
    body_truncated  boolean     NOT NULL DEFAULT false
);

-- The ping log: "latest pings for this check", newest first.
CREATE INDEX pings_check_received_idx ON pings (check_id, received_at DESC);

CREATE TABLE status_changes (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    check_id     bigint      NOT NULL REFERENCES checks (id) ON DELETE CASCADE,
    from_status  text        NOT NULL CHECK (from_status IN ('NEW', 'UP', 'LATE', 'DOWN', 'PAUSED')),
    to_status    text        NOT NULL CHECK (to_status IN ('NEW', 'UP', 'LATE', 'DOWN', 'PAUSED')),
    reason       text        NOT NULL CHECK (length(reason) <= 200),
    changed_at   timestamptz NOT NULL
);

-- The status timeline: "changes for this check", newest first.
CREATE INDEX status_changes_check_changed_idx ON status_changes (check_id, changed_at DESC);
