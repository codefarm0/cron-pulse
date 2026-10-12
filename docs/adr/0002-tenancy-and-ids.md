# 2. Tenancy column and ID strategy from the first migration

**Status:** Accepted
**Date:** 2026-10-12

## Context
CronPulse will be multi-tenant (organisations, Phase 4) and expects 100k checks and hundreds of millions of pings. Login doesn't exist yet in Phase 1, so it would be easy to postpone tenancy and use whatever IDs the framework defaults to. Both decisions are very expensive to change once tables are large and IDs are in customers' scripts.

## Decision
1. **Every top-level tenant-owned table has `organization_id NOT NULL`** from V1: `checks`, `alert_channels` (and later `api_keys`, `audit_events`). Login arrives later; until then a single default organisation is used.
2. **Child rows don't repeat the tenant column.** `pings`, `status_changes` and `check_channels` are always read *through* their check, which has already been tenant-checked. Adding `organization_id` to ~200M pings/month would cost space and index size for no extra safety.
3. **Two IDs per resource:** an internal `bigint` identity (`id`) for joins and foreign keys, and a random UUID (`public_id`) that is the only ID the API and UI expose. Internal IDs leak row counts and creation order and invite enumeration; random UUIDs don't, and they decouple the API from the database.
4. **The ping UUID is separate from `public_id`.** It's a *capability*: whoever knows it can report on the job. Keeping it separate means it can be rotated without changing the check's identity, and it never appears in management URLs or logs.
5. **Tables are plural** (`checks`, not `check`). `CHECK` is a reserved word in SQL.

## Consequences
- Every repository method a user can trigger takes `organizationId` (e.g. `findByOrganizationIdAndPublicId`). Tests assert that another tenant's ID returns nothing.
- `public_id` lookups use a unique index; a random UUID index is larger and less cache-friendly than a `bigint` one, which is acceptable for management traffic. The hot ping path looks up `ping_uuid` once and is cached (Phase 2).
- Phase 4 replaces the default-organisation resolver without any schema change.
