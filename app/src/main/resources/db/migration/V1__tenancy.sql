-- Tenancy comes first: every resource in CronPulse belongs to an organisation.
-- Login arrives in Phase 4, but adding a tenant column to big tables later is slow and risky,
-- so the column exists from the very first migration.
--
-- ID strategy used in every table:
--   id         bigint identity: internal, compact, fast joins. Never leaves the service.
--   public_id  random UUID: what the API and UI expose. Doesn't leak row counts or creation order.

CREATE TABLE organizations (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   uuid        NOT NULL UNIQUE,
    name        text        NOT NULL CHECK (length(name) BETWEEN 1 AND 100),
    slug        text        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    plan        text        NOT NULL DEFAULT 'FREE' CHECK (plan IN ('FREE', 'PRO')),
    created_at  timestamptz NOT NULL
);

CREATE TABLE app_users (
    id             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id      uuid        NOT NULL UNIQUE,
    email          text        NOT NULL CHECK (length(email) <= 254),
    display_name   text        NOT NULL CHECK (length(display_name) BETWEEN 1 AND 100),
    -- Filled in Phase 4 (form login). Null for users who sign in with an external provider.
    password_hash  text,
    created_at     timestamptz NOT NULL
);

-- Emails are compared case-insensitively: Alice@x.com and alice@x.com are the same person.
CREATE UNIQUE INDEX app_users_email_uq ON app_users (lower(email));

CREATE TABLE memberships (
    organization_id  bigint      NOT NULL REFERENCES organizations (id),
    user_id          bigint      NOT NULL REFERENCES app_users (id),
    role             text        NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'VIEWER')),
    created_at       timestamptz NOT NULL,
    PRIMARY KEY (organization_id, user_id)
);

-- "Which organisations does this user belong to?" runs on every login.
CREATE INDEX memberships_user_idx ON memberships (user_id);
