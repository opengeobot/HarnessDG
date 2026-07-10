# Publish Governance Runbook

## Overview

The publish saga (`VERSION_PUBLISH` job) coordinates approval, Gitea tag creation, and PostgreSQL state transitions. This runbook documents failure modes, compensation, and operator actions.

## Saga steps

| Step | Action | Failure signal |
|------|--------|----------------|
| P1 | Confirm `publish_request` is `APPROVED` or `PUBLISHING` | Job no-ops if status wrong |
| P2 | Version must be `PENDING_REVIEW` | `FAILED` + `IllegalStateException` |
| P3 | Frozen digest/commit must match version | `FAILED` + digest/commit drift |
| P4 | Create Gitea protected tag (or PG-only) | `DependencyException` (retryable) or tag conflict |
| P5–P6 | Mark version `PUBLISHED`, request `PUBLISHED` | — |

## Compensation matrix

### Tag conflict (PG or Gitea)

- **Symptom**: `git tag conflict` in job attempt; `publish_request.status = FAILED`.
- **Cause**: Another published version already owns the tag, or Gitea tag points to a different commit.
- **Compensation**: Do **not** overwrite the tag. Investigate duplicate version literals; deprecate or rename the draft; create a new publish request after re-validation.
- **PG state**: Version stays `PENDING_REVIEW`; request `FAILED`. No partial publish.

### Gitea unavailable

- **Symptom**: `GITEA_DEPENDENCY_UNAVAILABLE` on job attempt; job enters `RETRY_WAIT`.
- **Cause**: Gitea REST down, network partition, or invalid token.
- **Compensation**: Restore Gitea connectivity; job retries with exponential backoff. **Do not** manually set version to `PUBLISHED` until tag creation succeeds or Gitea is intentionally disabled (PG-only mode).
- **PG state**: Request may be `PUBLISHING`; version remains `PENDING_REVIEW` until success.

### Digest / commit drift

- **Symptom**: `manifest digest mismatch` or `source commit drift` during publish or review.
- **Cause**: Content changed after submit/approve freeze point.
- **Compensation**: Transition version back to `DRAFT`; re-run validation; submit a new publish request. Reject stale approvals.
- **PG state**: Request marked `FAILED`; version should be returned to `DRAFT` by reviewer action or operator correction.

### Reconciliation discrepancies

`PUBLISHED_VERSION_RECONCILE` compares PG triplets with Gitea tag, commit, and `aihub/manifest.json` digest.

| Classification | Meaning | Operator action |
|----------------|---------|-----------------|
| `MANUAL_REVIEW` | Missing tag/manifest, invalid PG format, or Gitea unavailable in compose/production | Inspect Gitea repo; restore tag or re-publish |
| `SECURITY_INCIDENT` | Commit or manifest digest mismatch | Halt downloads; incident response; compare Gitea history vs PG audit |
| `CONSISTENT` | PG and Gitea align | None |

## Multi-reviewer policy

Assets with sensitivity `CONFIDENTIAL`, `SECRET`, `HIGH`, or `RESTRICTED` require **two distinct** `APPROVE` decisions before the publish job is enqueued. Lower sensitivities require one approver. Submitters cannot approve their own request.

## Manual recovery checklist

1. Query `publish_request` and `review_decision` for the version.
2. Confirm `asset_version.manifest_digest` and `source_commit` match the frozen request fields.
3. Verify Gitea tag exists and points to the frozen commit.
4. If job is `DEAD`, fix root cause and use `POST /api/v1/system/jobs/{jobId}:retry` (admin).
5. Record actions in the audit trail; do not delete published rows.

## Related jobs

- `VERSION_VALIDATE` — must produce `PASSED` report before submit.
- `PUBLISHED_VERSION_RECONCILE` — periodic drift detection.
