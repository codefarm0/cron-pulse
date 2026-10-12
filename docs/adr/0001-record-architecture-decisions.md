# 1. Record architecture decisions

**Status:** Accepted
**Date:** 2026-10-12

## Context
CronPulse is built in public, and every phase becomes a story. Readers (and future us) need to know not just *what* we built but *why*, including the options we turned down.

## Decision
We record each decision that has a real trade-off as a short ADR in `docs/adr/NNNN-title.md`, with three sections: **Context**, **Decision**, **Consequences**. ADRs are never edited after being accepted; a later ADR supersedes an earlier one.

## Consequences
- Stories link to ADRs instead of re-explaining trade-offs.
- Writing an ADR takes ten minutes; skipping one costs much more when the decision is questioned later.
