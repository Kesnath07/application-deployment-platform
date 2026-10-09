# Operations Guide

Day-2 reference for running the control center: what works without any cloud account, how
health checks, the deployment lifecycle and rollbacks behave, how errors are reported, and how to
troubleshoot common situations. First-time AWS setup is in [deployment.md](deployment.md).

## What runs where

| Capability | Locally (Docker Compose or `./mvnw`) | Requires external setup |
|---|---|---|
| Applications, environments, deployment records, history, rollback records, dashboard and REST API | Yes | PostgreSQL (Compose starts one) |
| Health probes of environment URLs | Yes, for any URL the app can reach | An environment URL |
| Status reporting (`RUNNING`/`SUCCESS`/`FAILED`) | Yes, via the API or the buttons on the deployment page | — |
| Triggering the GitHub Actions deploy workflow from the dashboard | No | A fine-grained GitHub token (`GITHUB_TOKEN`, *Actions: Read and write*) and a `deploy.yml` workflow in the target repository |
| Automatic status callbacks from the pipeline | No | Repository variable `CONTROL_CENTER_URL`, reachable from GitHub-hosted runners |
| Building and pushing images, deploying to ECS, verifying `/api/health` | No | AWS account, applied Terraform, GitHub environments with the OIDC role variables |
| CloudWatch logs, alarms and dashboard | No | AWS |
| Terraform `fmt`, `validate`, Checkov | Yes, no credentials needed | — |
| Terraform `plan` / `apply` | No | AWS credentials and the S3 state bucket |

Without a GitHub token every deployment is still recorded. It stays `PENDING` with a note telling
you to run the workflow yourself and report the result.

## Configuration reference

All settings are environment variables. Locally they come from `.env` (copy `.env.example`), in
AWS from the ECS task definition and Secrets Manager.

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `5432`, `controlcenter` | PostgreSQL location |
| `DB_USERNAME`, `DB_PASSWORD` | `controlcenter`, empty | Credentials (Secrets Manager in AWS) |
| `DB_SSL_MODE` | `prefer` | `require` in AWS (RDS enforces TLS) |
| `DB_POOL_SIZE` | `10` | Maximum JDBC connections |
| `APP_VERSION`, `APP_ENVIRONMENT` | `local` | Reported by `/api/health`; the deploy workflow sets `APP_VERSION` to the image tag |
| `APP_LOG_LEVEL` | `INFO` | Log level of the `com.controlcenter` packages |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | unset | `ecs` in AWS for JSON logs |
| `HEALTH_CHECK_ENABLED` | `true` | Background probes of environment URLs |
| `HEALTH_CHECK_INTERVAL` | `PT60S` | Delay between probe rounds (ISO-8601, must be positive) |
| `HEALTH_CHECK_TIMEOUT` | `PT3S` | Connect and read timeout of one probe (must be positive) |
| `GITHUB_DISPATCH_ENABLED`, `GITHUB_TOKEN` | `false`, empty | Trigger the deploy workflow from the dashboard |
| `GITHUB_WORKFLOW_FILE` | `deploy.yml` | Workflow file dispatched in the target repository |

Invalid health-check settings stop the application at startup with a message naming the
property, e.g. `control-center.health-check.path must start with '/'`. Enabling GitHub dispatch
without a token is not fatal: a warning is logged at startup and deployments stay `PENDING`
with a note that the token is missing.

## Health checks

| Endpoint | Checks | Used by |
|---|---|---|
| `GET /api/health` | Process is up; returns `status`, `version`, `environment`. Does **not** touch the database, so a short RDS failover does not make ECS replace healthy tasks | ALB target group, ECS container health check, Dockerfile `HEALTHCHECK`, deploy workflow verification, the control center's own probes |
| `GET /actuator/health` | Aggregate status including the database connection (details are hidden) | Manual checks, monitoring |
| `GET /actuator/health/liveness`, `/actuator/health/readiness` | Spring Boot availability state | Available for orchestrators; not wired to the ALB |
| `GET /actuator/info` | Build metadata (artifact, version, build time) | Manual checks |

No other Actuator endpoint is exposed.

**Probes of registered environments.** Every `HEALTH_CHECK_INTERVAL` the control center calls
`<environment url>/api/health` (redirects are not followed). A 2xx response marks the environment
`HEALTHY`; anything else marks it `UNHEALTHY` and stores the reason as `healthDetail`:

| Detail | Typical cause |
|---|---|
| `HTTP 503 Service Unavailable` (or another status) | The workload answered with an error, e.g. no healthy targets behind the ALB |
| `No response within 3000 ms` | Timeout: overloaded tasks or a security group dropping packets |
| `Connection refused` | Nothing listens at the URL or port |
| `Unknown host …` | DNS name is wrong or was deleted |

The detail is shown on the environment page, as a tooltip on health badges, in the dashboard's
**Needs attention** panel, and as `healthDetail` in `GET /api/environments/{id}` and
`GET /api/environments/{id}/status`. It is cleared when a probe succeeds or the URL changes.
*Run health check* on the environment page (or `POST /api/environments/{id}/health-check`) probes
immediately.

The dashboard's **Needs attention** panel lists every environment that is `FAILED`, `DOWN` or
`DEGRADED`, with the failed deployment's last pipeline note or the failing probe's detail.

## Deployment lifecycle guarantees

- **One deployment in progress per environment.** Starting a deployment or rollback locks the
  environment row, so concurrent requests are serialized; the second one gets `409`.
- **Status reports are applied one at a time.** The deployment row is locked while a report is
  applied, so racing `SUCCESS` and `FAILED` callbacks cannot both win.
- **Retried callbacks are safe.** The deploy workflow posts its result with `curl --retry`. If the
  first attempt was applied but its response was lost, the retry is acknowledged with `200`.
- **Dispatch failures never leave a deployment stuck.** If GitHub rejects the dispatch, is
  unreachable, or the dispatcher fails unexpectedly, the deployment is marked `FAILED` with the
  reason in its log. An environment with a live version keeps serving it and stays `ACTIVE`.

| Report | Deployment is | Response |
|---|---|---|
| `RUNNING` | `PENDING` | `200` |
| `SUCCESS` or `FAILED` | `PENDING` or `RUNNING` | `200` |
| The same `SUCCESS` / `FAILED` it already has | `SUCCESS` / `FAILED` | `200`, nothing changes |
| `RUNNING` again, or a different final status | already `RUNNING` / finished | `409` |
| `PENDING` or `ROLLED_BACK` | any | `400` (`ROLLED_BACK` is set automatically) |
| any | unknown id | `404` |

## Rollback behaviour

A rollback is a new deployment of an image that was already live in the same environment. The
pipeline skips the build because the image is in ECR. History is append-only: the restored
deployment is never modified, and the deployment it replaces becomes `ROLLED_BACK` only after
the rollback has succeeded. A failed rollback leaves the live version untouched.

A target is eligible when it is a `SUCCESS` deployment of the same environment whose image is
not the one currently live. Without `targetDeploymentId` the newest eligible one is used. The
*Roll back to &lt;version&gt;* button always sends the deployment it names, so a deployment that
finishes after the page loaded cannot change what the button restores.

| Response | When |
|---|---|
| `404` | Unknown environment or target deployment |
| `400` | Target belongs to another environment, `targetDeploymentId` is not positive, or `reason` exceeds 500 characters |
| `409` | Nothing is live yet; no earlier version with a different image exists; a deployment is in progress; or the target is ineligible. The message says why: it is the live deployment, still in progress, failed and never went live, was rolled back (deploy its image tag again instead), or runs the image that is already live |

ECS adds an independent safety net: the deployment circuit breaker returns the service to the
last working task definition when new tasks never become healthy.

## API errors

Every `/api` error has the same JSON shape:

```json
{
  "timestamp": "2026-10-09T10:15:30Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/deployments/42/status",
  "details": ["status: must be one of PENDING, RUNNING, SUCCESS, FAILED, ROLLED_BACK"]
}
```

| Status | Meaning |
|---|---|
| `400` | Invalid input. Field problems are listed in `details`; invalid path or query parameters are named in `message` |
| `404` | Unknown application, environment, deployment or API route |
| `405` | HTTP method not supported; `message` and the `Allow` header list the supported ones |
| `409` | Conflicts with the current state (deployment in progress, image already live, illegal transition, ineligible rollback target, or a concurrent request that won a uniqueness check) |
| `415` | Body is not `application/json` |
| `500` | Unexpected error; details are logged, never returned |

Errors raised by API endpoints are JSON even when the client's `Accept` header asks for another
format.

## Data integrity

Flyway applies the migrations in `src/main/resources/db/migration` at startup:

| Migration | Content |
|---|---|
| `V1__create_core_schema.sql` | Tables, foreign keys, status checks, base indexes |
| `V2__add_environment_health_detail.sql` | `environments.last_health_detail` |
| `V3__strengthen_deployment_integrity.sql` | Deployment application must own its environment; timestamps consistent with status; no blank or `latest` image tags; `ACTIVE` environments have a version; indexes for live/rollback lookups and history pages |

All migrations are additive. If a row edited by hand violates a V3 constraint, PostgreSQL rolls
the migration back and the application does not start; no data is changed. These queries find
such rows before an upgrade:

```sql
SELECT d.id FROM deployments d JOIN environments e ON e.id = d.environment_id
WHERE d.application_id <> e.application_id;

SELECT id, status, started_at, completed_at FROM deployments
WHERE NOT ((status = 'PENDING' AND started_at IS NULL AND completed_at IS NULL)
        OR (status = 'RUNNING' AND started_at IS NOT NULL AND completed_at IS NULL)
        OR (status IN ('SUCCESS', 'FAILED', 'ROLLED_BACK') AND started_at IS NOT NULL AND completed_at IS NOT NULL))
   OR completed_at < started_at;

SELECT id, name FROM environments WHERE status = 'ACTIVE' AND current_version IS NULL;
```

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Deployment stays `PENDING` | GitHub dispatch is disabled or the token is missing (the deployment log says which). Run the workflow manually, then report the result with `POST /api/deployments/{id}/status` or the *Mark SUCCESS* / *Mark FAILED* buttons. Until then, new deployments and rollbacks of that environment return `409` |
| Deployment stays `RUNNING` | The pipeline's callback never arrived: `CONTROL_CENTER_URL` is not set, is unreachable from GitHub runners, or the report job failed. Check the workflow run and report the result manually |
| Environment is `DOWN` | Read `healthDetail` (see [Health checks](#health-checks)) and the ALB target health in the AWS console |
| Environment is `DEGRADED` | The latest deployment failed while the previous version keeps serving. The failure reason is on the environment page and in `failureReason` |
| `409 … already live` | The image is the one currently serving traffic; deploy a different tag or roll back instead |
| Application does not start after an upgrade | Check the log for a Flyway validation error (see [Data integrity](#data-integrity)) or an invalid health-check property |
| Startup warning `GITHUB_DISPATCH_ENABLED is true but GITHUB_TOKEN is empty` | Provide the token (in AWS: `github_token_secret_arn` in `terraform.tfvars`) or disable dispatch |
