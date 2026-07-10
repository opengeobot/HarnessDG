# HarnessDG AI Engineering Guide

## Current phase

This repository has completed **P0-B platform-foundation remediation** (VERIFIED). P0-A engineering baseline is complete; P0-B exit gate has been validated at E1/E2/E3/E4 evidence levels including Compose full-stack verification (V01-V21 all PASS).

**P1–P5 business waves are PARTIAL / IMPLEMENTED_UNVERIFIED**: substantial catalog, version/transfer, publish, MCP, and ops code exists, but wave exit Evidence Manifests remain DRAFT and several deep gaps (real multipart/DVC materialization, asset ACL UX, REST `/agent/*`, E4/E5 drills) are still open. Do not present P1–P5 as VERIFIED.

Do not present planned P0 capabilities as implemented. The dated implementation snapshot for current P1–P5 status is `docs/architecture/implementation-status-2026-07-10.md` (P0-B closure history remains in `implementation-status-2026-07-02.md`).
Use `docs/architecture/p0-platform-foundation-traceability.md` to check every P0-B task across module, data, API, permission, audit, UI, and verification.

## Required reading

Before planning or changing product code, read:

1. `prd/DEEP_RESEARCH_内部AI资产管理平台设计.md`
2. `docs/ai-spec/manifest.yaml` and `docs/ai-spec/06-ide/generic-execution-protocol.md`
3. The formal `docs/ai-spec/tasks/TASK-*.md` Task Card and every referenced REQ/AC/DEC
4. `docs/adr/ADR-0001-technology-baseline.md`
5. `docs/adr/ADR-0002-p0-platform-foundation-gate.md`
6. Relevant OpenAPI, MCP, Flyway, event schema, module, and runbook documentation

The mandatory AI development rules are in section 15 of the design document. The platform-wide capabilities are in section 5.

## AI implementation gate

`DEC-009` makes the AI Spec execution protocol mandatory for Trae, Codex, Claude Code, Cursor, Qwen Code,
and other AI programming IDEs.

Before editing product code, contracts, Flyway, deployment, or tests:

```powershell
./docs/ai-spec/tools/validate-spec.ps1
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath <formal-task-card>
```

Implementation is forbidden when the Task Card is missing or validation fails. A valid implementation Task must
be `READY`, have `implementationAuthorized: true`, reference only `READY` requirements and `ACCEPTED`
decisions, bind a real base Commit, list narrow allowed paths, prove its upstream stage gate, and map every
acceptance scenario to expected evidence. AI may draft specifications and Task Cards but must not approve or
authorize its own Task in the same implementation session.

Before handoff, rerun the Task validator with `-CheckChangedPaths`; any changed file outside approved
`allowedPaths` blocks handoff. A completion claim additionally requires `-CheckCompletion`, a clean current
Commit, full AC-linked PASS Evidence at the required level, no SKIP/notProven, redaction confirmation, and
acceptance by the verification authority. A failed completion check means `IMPLEMENTED_UNVERIFIED`, not done.

Local validators guide every IDE, but they are not a security boundary. Until `TASK-GOV-008` connects them to
protected CI and removes non-blocking contract checks, bypass by a non-compliant IDE remains a known gate gap.

The accepted end-state for dataset experience and AI data workflows is
`docs/ai-spec/01-requirements/dataset-experience.md` (`DEC-008`). It is a future P1-P4 target, not permission
to bypass the current P0-B gate.

## Fixed architecture baseline

- Gitea owns Git repositories, code, cards, manifests, commits, and tags.
- DVC owns large-file content versioning.
- MinIO is the object-storage data plane.
- PostgreSQL stores workflow state, authorization, tasks, audit, and query projections.
- The business control plane is Java 21 + Spring Boot + MyBatis-Plus.
- The frontend is React + TypeScript.
- Docker Compose is the development and functional-verification baseline.
- REST, MCP, and Worker adapters must reuse the same application services, authorization, state machines, and audit logic.
- PostgreSQL local accounts are the P0 identity source; Web, REST, Agent, and MCP authenticate with platform-issued JWTs.
- Dictionaries are for configurable classifications; stable workflow states remain code enums and database constraints.
- Tags are governed platform/organization resources referenced by `tagId`, not free-form asset strings.

Do not replace this technology route or change source-of-truth boundaries without an accepted ADR.

## Mandatory engineering rules

- Keep the backend as a modular monolith until an accepted ADR says otherwise.
- Controllers and MCP tools are adapters; they must not contain business rules or call Mappers directly.
- Domain code must not depend on Spring, MyBatis, Gitea, MinIO, or DVC SDK details.
- Never return persistence entities from APIs.
- Never parse tokens inside business modules; use the shared principal context.
- Enforce authorization in the backend and filter unauthorized data in database queries.
- Authentication and authorization fail closed. Anonymous access is limited to user login, Agent/Client credential exchange, token refresh, and minimal health endpoints. A null principal, allow-all policy, or default all-scopes provider must never enter a deployable profile.
- Business modules must reuse the platform identity, authorization, taxonomy, configuration, idempotency/job, audit/logging, notification, and observability APIs. Do not create local substitutes.
- Owner fields reference Principal/Team IDs. Governed fields reference active dictionary `itemCode` values. Asset writes accept only registered `tagId` values.
- Stable workflow states are code enums and database constraints, not dynamic dictionary entries.
- Durable DVC, publish, webhook, preview, and reconciliation work goes through the persistent job system, not raw threads or `@Async`.
- Cross-system consistency uses Saga, Inbox, Outbox, idempotency, and reconciliation.
- Published versions are immutable and identified by Git Tag + Commit SHA + Manifest/DVC Digest.
- Secrets, permanent object-store credentials, tokens, and presigned URLs must not enter Git, logs, test fixtures, or AI conversation output.
- Browser access JWTs stay in memory; refresh JWTs use the shared secure-cookie/rotation implementation. Never store JWTs in LocalStorage, analytics, or error reports.
- Database changes are forward-only Flyway migrations. Never edit an applied migration.
- Do not weaken validation, authorization, database constraints, or tests to make a task pass.

## Contract-first workflow

For behavior changes:

1. Identify the use case, permission, preconditions, idempotency, errors, and audit event.
2. Identify use of identity/authorization, dictionary/tag/i18n, configuration, job/idempotency, logging/audit, notification, metrics, traces, and alerts.
3. Update OpenAPI/MCP/event contracts first when applicable.
4. Add a forward Flyway migration when data changes. Never modify `V1` or `V2`; remediation starts at `V3+`.
5. Implement application/domain behavior through shared platform APIs.
6. Implement REST, MCP, Worker, frontend, and infrastructure adapters as required.
7. Add unit, integration, contract, authorization, audit, and failure-path tests.
8. Update Compose fixtures/verification and affected design/runbook documentation.

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

P0-B additionally requires OpenAPI lint/breaking-change checks and Compose verification for login/JWT lifecycle, user disablement, permission filtering, dictionaries/tags, log redaction, audit completeness, persistent jobs, notifications, and observability. A test skipped because Docker is unavailable is not a passing integration test.

## Definition of done

A task is complete only when:

- acceptance criteria are met;
- architecture and module boundaries are preserved;
- contracts, migrations, generated types, fixtures, and docs are synchronized;
- authorization, idempotency, error mapping, audit, and observability are handled;
- dictionary/tag/i18n, configuration, logging, notification, metrics, and trace impacts are handled;
- normal, boundary, failure, and unauthorized paths are tested;
- relevant validation results and any unverified items are reported;
- no unrelated refactor, dependency upgrade, secret, backdoor, or placeholder implementation was added.
- no null-principal, allow-all, all-scopes, free-form governed-value, or in-memory reliable-work fallback was added.

End implementation reports with:

```text
Completed:
Changed files:
Contract/database changes:
Validation run and results:
Validation not run and reasons:
Remaining risks or follow-ups:
```
