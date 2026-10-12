-- Phase 1: prove the indexes are used at realistic volume.
-- Loads 100k checks and 1M pings inside a transaction, runs the hot queries with EXPLAIN ANALYZE,
-- then rolls everything back. Run against the dev database:
--   docker compose exec -T postgres psql -U cronpulse -d cronpulse < docs/learnings/sql/phase-1-explain.sql
BEGIN;

INSERT INTO checks (public_id, organization_id, ping_uuid, name, slug, schedule_type, period_seconds,
                    grace_seconds, status, last_ping_at, next_expected_at, created_at, updated_at)
SELECT gen_random_uuid(), (SELECT id FROM organizations WHERE slug = 'demo'), gen_random_uuid(),
       'load-' || n, 'load-' || n, 'SIMPLE', 3600, 300,
       -- realistic mix: most checks are UP, a few are NEW / DOWN / PAUSED
       (ARRAY['UP','UP','UP','UP','UP','UP','UP','NEW','DOWN','PAUSED'])[1 + n % 10],
       now() - interval '30 minutes',
       now() + make_interval(secs => (n % 7200) - 600),   -- ~8% already overdue
       now(), now()
FROM generate_series(1, 100000) AS n;

INSERT INTO pings (check_id, type, received_at, method)
SELECT c.id, 'SUCCESS', now() - make_interval(mins => p), 'GET'
FROM (SELECT id FROM checks WHERE slug LIKE 'load-%' ORDER BY id LIMIT 10000) c
CROSS JOIN generate_series(1, 100) AS p;

ANALYZE checks;
ANALYZE pings;

\echo '--- 1. Detector: overdue UP/LATE checks (partial index checks_due_idx)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT id FROM checks
WHERE status IN ('UP', 'LATE') AND next_expected_at < now()
ORDER BY next_expected_at
LIMIT 100;

\echo '--- 2. Ping log: newest 50 pings of one check (pings_check_received_idx)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT * FROM pings
WHERE check_id = (SELECT min(check_id) FROM pings)
ORDER BY received_at DESC
LIMIT 50;

\echo '--- 3. Ping ingest lookup by ping_uuid (unique index)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT * FROM checks WHERE ping_uuid = (SELECT ping_uuid FROM checks WHERE slug = 'load-4242');

\echo '--- Index sizes: the partial index only holds UP/LATE rows'
SELECT relname AS index, pg_size_pretty(pg_relation_size(oid)) AS size
FROM pg_class WHERE relname IN ('checks_due_idx', 'checks_ping_uuid_key', 'pings_check_received_idx');

ROLLBACK;
