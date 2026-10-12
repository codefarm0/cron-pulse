-- Dev-only seed data (loaded only with the `dev` profile; see application-dev.yml).
-- Recreates a demo organisation whose checks cover every status, matching docs/ui/Main.html,
-- so the dashboard looks real on first run.
--
-- This is a *repeatable* migration: Flyway re-runs it whenever this file changes, after all
-- versioned migrations. It deletes and recreates the demo data, so it is safe to run again.
-- Times are relative to now(), so they are "fresh" each time the seed runs.

DELETE FROM check_channels WHERE check_id IN (SELECT c.id FROM checks c JOIN organizations o ON o.id = c.organization_id WHERE o.slug = 'demo');
DELETE FROM checks         WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'demo');
DELETE FROM alert_channels WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'demo');
DELETE FROM memberships    WHERE organization_id IN (SELECT id FROM organizations WHERE slug = 'demo');
DELETE FROM organizations  WHERE slug = 'demo';
DELETE FROM app_users      WHERE email = 'demo@cronpulse.local';

INSERT INTO organizations (public_id, name, slug, plan, created_at)
VALUES ('00000000-0000-4000-8000-000000000001', 'Demo Org', 'demo', 'FREE', now() - interval '30 days');

INSERT INTO app_users (public_id, email, display_name, created_at)
VALUES ('00000000-0000-4000-8000-000000000002', 'demo@cronpulse.local', 'Demo User', now() - interval '30 days');

INSERT INTO memberships (organization_id, user_id, role, created_at)
SELECT o.id, u.id, 'OWNER', now() - interval '30 days'
FROM organizations o, app_users u
WHERE o.slug = 'demo' AND u.email = 'demo@cronpulse.local';

-- Six checks, one per status (two UP). Cron expressions use the classic 5-field format users write.
-- Fixed ping UUIDs so the README's curl examples work against the dev database.
INSERT INTO checks (public_id, organization_id, ping_uuid, name, slug, tags,
                    schedule_type, period_seconds, cron_expression, time_zone, grace_seconds,
                    status, last_ping_at, next_expected_at, paused_at, created_at, updated_at)
SELECT v.public_id::uuid, o.id, v.ping_uuid::uuid, v.name, v.name, v.tags,
       v.schedule_type, v.period_seconds, v.cron_expression, v.time_zone, v.grace_seconds,
       v.status, v.last_ping_at, v.next_expected_at, v.paused_at, now() - interval '30 days', now()
FROM organizations o,
(VALUES
  ('10000000-0000-4000-8000-000000000001', '7f3a9c2e-41d8-4b6e-9a0f-2c5d8e1b3a77', 'nightly-db-backup',   ARRAY['prod','postgres'],
   'CRON',   NULL,  '0 2 * * *',    'Asia/Kolkata', 900,  'DOWN',   now() - interval '1 day 4 hours', now() - interval '4 hours 10 minutes', NULL::timestamptz),
  ('10000000-0000-4000-8000-000000000002', '7f3a9c2e-41d8-4b6e-9a0f-2c5d8e1b3a78', 'invoice-generator',   ARRAY['billing'],
   'SIMPLE', 3600,  NULL,           'UTC',          900,  'LATE',   now() - interval '66 minutes',    now() - interval '6 minutes',          NULL),
  ('10000000-0000-4000-8000-000000000003', '7f3a9c2e-41d8-4b6e-9a0f-2c5d8e1b3a79', 'search-index-sync',   ARRAY['prod'],
   'SIMPLE', 900,   NULL,           'UTC',          300,  'UP',     now() - interval '3 minutes',     now() + interval '12 minutes',         NULL),
  ('10000000-0000-4000-8000-000000000004', '7f3a9c2e-41d8-4b6e-9a0f-2c5d8e1b3a7a', 'cleanup-temp-files',  ARRAY['ops'],
   'CRON',   NULL,  '*/30 * * * *', 'UTC',          300,  'UP',     now() - interval '11 minutes',    now() + interval '19 minutes',         NULL),
  ('10000000-0000-4000-8000-000000000005', '7f3a9c2e-41d8-4b6e-9a0f-2c5d8e1b3a7b', 'weekly-report-email', ARRAY['marketing'],
   'CRON',   NULL,  '0 9 * * MON',  'Asia/Kolkata', 3600, 'PAUSED', now() - interval '8 days',        NULL,                                  now() - interval '7 days'),
  ('10000000-0000-4000-8000-000000000006', '7f3a9c2e-41d8-4b6e-9a0f-2c5d8e1b3a7c', 'ml-feature-refresh',  ARRAY['data'],
   'SIMPLE', 21600, NULL,           'UTC',          1800, 'NEW',    NULL,                             NULL,                                  NULL)
) AS v(public_id, ping_uuid, name, tags, schedule_type, period_seconds, cron_expression, time_zone, grace_seconds,
       status, last_ping_at, next_expected_at, paused_at)
WHERE o.slug = 'demo';

-- A few days of successful pings for every check that has been pinged, spaced by its period.
-- (Cron checks use a fixed spacing here; real schedule maths arrives in Phase 2.)
INSERT INTO pings (check_id, type, exit_code, received_at, duration_ms, source_ip, user_agent, method, body)
SELECT c.id, 'SUCCESS', 0,
       c.last_ping_at - (n * make_interval(secs => COALESCE(c.period_seconds,
                         CASE c.slug WHEN 'nightly-db-backup' THEN 86400 WHEN 'weekly-report-email' THEN 604800 ELSE 1800 END))),
       (random() * 5000)::bigint, '10.0.4.17'::inet, 'curl/8.9.1', 'GET',
       CASE WHEN c.slug = 'nightly-db-backup' THEN 'pg_dump: dumped 42 tables (1.2 GB) in 38s' END
FROM checks c
JOIN organizations o ON o.id = c.organization_id AND o.slug = 'demo'
CROSS JOIN generate_series(0, 19) AS n
WHERE c.last_ping_at IS NOT NULL;

-- One failed run for search-index-sync, as in the mock's sparkline.
INSERT INTO pings (check_id, type, exit_code, received_at, source_ip, user_agent, method, body)
SELECT c.id, 'FAIL', 1, now() - interval '3 hours 2 minutes', '10.0.4.18'::inet, 'curl/8.9.1', 'POST',
       'ERROR: connection to search cluster timed out after 30s'
FROM checks c JOIN organizations o ON o.id = c.organization_id AND o.slug = 'demo'
WHERE c.slug = 'search-index-sync';

-- Status history behind the current statuses.
INSERT INTO status_changes (check_id, from_status, to_status, reason, changed_at)
SELECT c.id, v.from_status, v.to_status, v.reason, v.changed_at
FROM checks c
JOIN organizations o ON o.id = c.organization_id AND o.slug = 'demo'
JOIN (VALUES
  ('nightly-db-backup',   'NEW',  'UP',     'First ping received',                   now() - interval '20 days'),
  ('nightly-db-backup',   'UP',   'LATE',   'Expected ping not received',            now() - interval '4 hours 10 minutes'),
  ('nightly-db-backup',   'LATE', 'DOWN',   'Grace period of 15 minutes exceeded',   now() - interval '3 hours 55 minutes'),
  ('invoice-generator',   'NEW',  'UP',     'First ping received',                   now() - interval '20 days'),
  ('invoice-generator',   'UP',   'LATE',   'Expected ping not received',            now() - interval '6 minutes'),
  ('search-index-sync',   'NEW',  'UP',     'First ping received',                   now() - interval '20 days'),
  ('search-index-sync',   'UP',   'DOWN',   'Fail ping received (exit code 1)',      now() - interval '3 hours 2 minutes'),
  ('search-index-sync',   'DOWN', 'UP',     'Success ping received',                 now() - interval '2 hours 47 minutes'),
  ('cleanup-temp-files',  'NEW',  'UP',     'First ping received',                   now() - interval '20 days'),
  ('weekly-report-email', 'NEW',  'UP',     'First ping received',                   now() - interval '20 days'),
  ('weekly-report-email', 'UP',   'PAUSED', 'Paused by Demo User',                   now() - interval '7 days')
) AS v(slug, from_status, to_status, reason, changed_at) ON v.slug = c.slug;

-- One channel of each type, matching docs/ui/Channels.html.
INSERT INTO alert_channels (public_id, organization_id, type, name, target, verified, created_at)
SELECT v.public_id::uuid, o.id, v.type, v.name, v.target, true, now() - interval '30 days'
FROM organizations o,
(VALUES
  ('20000000-0000-4000-8000-000000000001', 'EMAIL',   'My email',     'demo@cronpulse.local'),
  ('20000000-0000-4000-8000-000000000002', 'SLACK',   'Ops alerts',   'https://hooks.slack.com/services/T000/B000/XXXX'),
  ('20000000-0000-4000-8000-000000000003', 'WEBHOOK', 'incident-bot', 'https://example.com/hooks/cronpulse')
) AS v(public_id, type, name, target)
WHERE o.slug = 'demo';

-- Email goes to every check; Slack to the production ones; the webhook to the backup only.
INSERT INTO check_channels (check_id, channel_id)
SELECT c.id, ch.id
FROM checks c
JOIN alert_channels ch ON ch.organization_id = c.organization_id
JOIN organizations o ON o.id = c.organization_id AND o.slug = 'demo'
WHERE ch.type = 'EMAIL'
   OR (ch.type = 'SLACK' AND c.slug IN ('nightly-db-backup', 'invoice-generator', 'search-index-sync', 'cleanup-temp-files'))
   OR (ch.type = 'WEBHOOK' AND c.slug = 'nightly-db-backup');
