-- Stored responses for POST requests that carried an Idempotency-Key header.
-- If a client retries (e.g. after a timeout), the stored response is replayed instead of
-- creating a second check. Keys are scoped per organisation and expire after 24 hours.

CREATE TABLE idempotency_keys (
    organization_id  bigint      NOT NULL REFERENCES organizations (id),
    idempotency_key  text        NOT NULL CHECK (length(idempotency_key) BETWEEN 1 AND 255),
    -- SHA-256 of the request; the same key with a different request is a client bug and is rejected.
    request_hash     text        NOT NULL,
    response_status  integer,
    response_body    text,
    location         text,
    created_at       timestamptz NOT NULL,
    PRIMARY KEY (organization_id, idempotency_key)
);

-- The hourly cleanup deletes expired keys by age.
CREATE INDEX idempotency_keys_created_idx ON idempotency_keys (created_at);
