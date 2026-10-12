-- Where alerts go. Sending them (outbox, notifiers) arrives in Phase 3 with its own migration.

CREATE TABLE alert_channels (
    id               bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id        uuid        NOT NULL UNIQUE,
    organization_id  bigint      NOT NULL REFERENCES organizations (id),
    type             text        NOT NULL CHECK (type IN ('EMAIL', 'SLACK', 'WEBHOOK')),
    name             text        NOT NULL CHECK (length(name) BETWEEN 1 AND 100),
    -- An email address or an https URL, depending on type.
    target           text        NOT NULL CHECK (length(target) <= 2048),
    verified         boolean     NOT NULL DEFAULT false,
    created_at       timestamptz NOT NULL
);

CREATE INDEX alert_channels_org_idx ON alert_channels (organization_id);

-- Which channels each check alerts. Both sides belong to the same organisation; the service layer enforces that.
CREATE TABLE check_channels (
    check_id    bigint NOT NULL REFERENCES checks (id) ON DELETE CASCADE,
    channel_id  bigint NOT NULL REFERENCES alert_channels (id) ON DELETE CASCADE,
    PRIMARY KEY (check_id, channel_id)
);

-- "Which checks use this channel?" (channels screen, Phase 4).
CREATE INDEX check_channels_channel_idx ON check_channels (channel_id);
