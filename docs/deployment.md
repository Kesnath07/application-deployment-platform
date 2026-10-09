# Deployment Guide

This guide takes an empty AWS account to a running control center and covers day-2 operations.
Commands assume the `dev` environment. Repeat them with `prod` where noted.

## Prerequisites

- AWS account and an IAM principal that can create VPC, ECS, ECR, RDS, IAM, Secrets Manager and CloudWatch resources
- AWS CLI v2, Terraform ≥ 1.11
- A GitHub repository containing this code (`owner/name`)

## 1. Create the Terraform state bucket (once per account)

```bash
aws s3api create-bucket --bucket <state-bucket> --region eu-central-1 \
  --create-bucket-configuration LocationConstraint=eu-central-1
aws s3api put-bucket-versioning --bucket <state-bucket> --versioning-configuration Status=Enabled
aws s3api put-public-access-block --bucket <state-bucket> --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

State locking uses S3 native lock files (`use_lockfile = true`), so no DynamoDB table is needed.

## 2. Provision the environment

```bash
cd infra/terraform/environments/dev
cp backend.hcl.example backend.hcl            # set bucket + region
cp terraform.tfvars.example terraform.tfvars  # set github_repository, alb_ingress_cidrs
terraform init -backend-config=backend.hcl
terraform plan -out=tfplan
terraform apply tfplan
```

Notes:

- **GitHub OIDC provider.** It is account-wide. `dev` creates it (`create_github_oidc_provider = true`) and `prod` looks it up, so apply `dev` first when both share an account. If they live in separate accounts, set it to `true` in both.
- **First apply.** The ECS service starts with the placeholder tag `bootstrap`. That image doesn't exist, so tasks fail to start until the first pipeline run pushes a real image. This is expected and harmless.

## 3. Configure GitHub

Create the GitHub environments **`dev`** and **`prod`** (*Settings → Environments*). Add the
variables printed by:

```bash
terraform output github_environment_variables
```

| Variable | Example |
|---|---|
| `AWS_REGION` | `eu-central-1` |
| `AWS_DEPLOY_ROLE_ARN` | `arn:aws:iam::123456789012:role/control-center-dev-github-deploy` |
| `ECR_REPOSITORY` | `control-center-dev` |
| `ECS_CLUSTER` | `control-center-dev-cluster` |
| `ECS_SERVICE` | `control-center-dev-service` |
| `ECS_TASK_DEFINITION` | `control-center-dev` |
| `ECS_CONTAINER_NAME` | `app` |
| `APP_URL` | `http://control-center-dev-alb-123.eu-central-1.elb.amazonaws.com` |

These are identifiers, not secrets. **No AWS access keys are stored in GitHub.**

Recommended protection for `prod`: required reviewers, and deployments limited to the `main` branch.

Optional repository variable `CONTROL_CENTER_URL` (for example the prod `APP_URL`). It lets the
workflow report results for deployments started from the dashboard.

## 4. First deployment

Push to `main`, or run **Actions → Deploy → Run workflow** (`environment = dev`). The workflow
tests, builds, pushes `:<commit-sha>` and updates the service. When it finishes:

```bash
curl "$(terraform output -raw alb_url)/api/health"
# {"status":"UP","version":"<commit-sha>","environment":"dev",...}
```

## 5. Register the platform in itself

Open the dashboard at `alb_url`:

1. Register the application with this repository's URL.
2. Create the `dev` and `prod` environments, using each environment's `alb_url` as its URL (this enables health probes).

## Promoting to production

Production uses the image tag that was already tested in dev:

- **GitHub:** *Run workflow* with `environment = prod` and `image_tag = <commit-sha>`, or
- **Dashboard:** open the `prod` environment, enter the SHA and click *Deploy*. This requires the GitHub token below.

`dev` and `prod` have separate ECR repositories. The first prod deployment of a SHA builds it
from that exact commit. Later redeploys reuse the image.

## Triggering deployments from the dashboard

1. Create a fine-grained GitHub token limited to the repository, with *Actions: Read and write*.
2. Store it in Secrets Manager:
   ```bash
   aws secretsmanager create-secret --name control-center-prod/github-token --secret-string '<token>'
   ```
3. Set `github_token_secret_arn` in `terraform.tfvars` and apply. The container then gets `GITHUB_TOKEN` and `GITHUB_DISPATCH_ENABLED=true`.

## Rolling back

- **Dashboard:** *Environment → Roll back to &lt;version&gt;*, or *Roll back to this* on any earlier successful deployment.
- **API:** `POST /api/environments/{id}/rollback` with optional `{"targetDeploymentId": 12, "reason": "..."}`. Ineligible targets are rejected with the reason; see [operations.md](operations.md#rollback-behaviour).
- **GitHub only:** *Run workflow* with the previous SHA as `image_tag`. The build is skipped because the image is in ECR.

When newly started tasks never become healthy, ECS also rolls back on its own (deployment circuit breaker).

For health checks, stuck deployments and other day-2 topics see [operations.md](operations.md).

## Rotating the database password

```bash
# terraform.tfvars
db_password_version = 2
terraform apply
aws ecs update-service --cluster <cluster> --service <service> --force-new-deployment
```

## Tearing down `dev`

```bash
terraform destroy
```

`dev` sets `force_delete` on ECR and skips the final RDS snapshot. `prod` keeps deletion
protection on both the ALB and RDS.
