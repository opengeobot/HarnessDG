# HarnessDG AI Engineering Guide

## Current phase

This repository is currently in the architecture and product-design phase. Do not invent missing implementation details as if they already exist. When implementation begins, preserve the architecture and contracts defined below.

## Required reading

Before planning or changing product code, read:

1. `prd/DEEP_RESEARCH_内部AI资产管理平台设计.md`
2. The current task and its acceptance criteria
3. Relevant ADR, OpenAPI, MCP, Flyway, event schema, and module documentation once those files exist

The mandatory AI development rules are in section 15 of the design document. The platform-wide capabilities are in section 5.

## Fixed architecture baseline

- Gitea owns Git repositories, code, cards, manifests, commits, and tags.
- DVC owns large-file content versioning.
- MinIO is the object-storage data plane.
- PostgreSQL stores workflow state, authorization, tasks, audit, and query projections.
- The business control plane is Java 21 + Spring Boot + MyBatis-Plus.
- The frontend is React + TypeScript.
- Docker Compose is the development and functional-verification baseline.
- REST, MCP, and Worker adapters must reuse the same application services, authorization, state machines, and audit logic.

Do not replace this technology route or change source-of-truth boundaries without an accepted ADR.

## Mandatory engineering rules

- Keep the backend as a modular monolith until an accepted ADR says otherwise.
- Controllers and MCP tools are adapters; they must not contain business rules or call Mappers directly.
- Domain code must not depend on Spring, MyBatis, Gitea, MinIO, or DVC SDK details.
- Never return persistence entities from APIs.
- Never parse tokens inside business modules; use the shared principal context.
- Enforce authorization in the backend and filter unauthorized data in database queries.
- Stable workflow states are code enums and database constraints, not dynamic dictionary entries.
- Durable DVC, publish, webhook, preview, and reconciliation work goes through the persistent job system, not raw threads or `@Async`.
- Cross-system consistency uses Saga, Inbox, Outbox, idempotency, and reconciliation.
- Published versions are immutable and identified by Git Tag + Commit SHA + Manifest/DVC Digest.
- Secrets, permanent object-store credentials, tokens, and presigned URLs must not enter Git, logs, test fixtures, or AI conversation output.
- Database changes are forward-only Flyway migrations. Never edit an applied migration.
- Do not weaken validation, authorization, database constraints, or tests to make a task pass.

## Contract-first workflow

For behavior changes:

1. Identify the use case, permission, preconditions, idempotency, errors, and audit event.
2. Update OpenAPI/MCP/event contracts first when applicable.
3. Add a forward Flyway migration when data changes.
4. Implement application/domain behavior.
5. Implement REST, MCP, Worker, frontend, and infrastructure adapters as required.
6. Add unit, integration, contract, authorization, and failure-path tests.
7. Update Compose fixtures/verification and affected design/runbook documentation.

If code, contracts, ADRs, and the design disagree, report the conflict. Do not silently pick one.

## Validation

Run the relevant commands once their project files exist:

```bash
./mvnw verify
pnpm lint
pnpm typecheck
pnpm test
pnpm build
docker compose config --quiet
./deploy/compose/scripts/verify.sh
```

Use the repository-provided equivalent on Windows. Never claim a check passed unless it was actually run. If a check cannot run, state why.

## Definition of done

A task is complete only when:

- acceptance criteria are met;
- architecture and module boundaries are preserved;
- contracts, migrations, generated types, fixtures, and docs are synchronized;
- authorization, idempotency, error mapping, audit, and observability are handled;
- normal, boundary, failure, and unauthorized paths are tested;
- relevant validation results and any unverified items are reported;
- no unrelated refactor, dependency upgrade, secret, backdoor, or placeholder implementation was added.

End implementation reports with:

```text
Completed:
Changed files:
Contract/database changes:
Validation run and results:
Validation not run and reasons:
Remaining risks or follow-ups:
```
