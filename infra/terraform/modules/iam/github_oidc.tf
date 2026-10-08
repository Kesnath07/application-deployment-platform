# GitHub Actions authenticates to AWS with short-lived OIDC tokens; no long-lived
# access keys exist anywhere. The trust policy only accepts tokens issued for jobs
# running in this repository's GitHub environment (e.g. "prod"), so a pull request
# or another branch cannot assume the role.

locals {
  github_oidc_url = "https://token.actions.githubusercontent.com"
  github_oidc_provider_arn = var.create_github_oidc_provider ? (
    aws_iam_openid_connect_provider.github[0].arn
  ) : data.aws_iam_openid_connect_provider.github[0].arn
}

resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 1 : 0

  url            = local.github_oidc_url
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 0 : 1

  url = local.github_oidc_url
}

data "aws_iam_policy_document" "github_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:environment:${var.github_environment}"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name                 = "${var.name}-github-deploy"
  description          = "Assumed by GitHub Actions (${var.github_repository}, environment ${var.github_environment}) to push images and deploy to ECS"
  assume_role_policy   = data.aws_iam_policy_document.github_assume.json
  max_session_duration = 3600
}

data "aws_iam_policy_document" "github_deploy" {
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"] # not resource-scoped
  }

  statement {
    sid = "EcrPushPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [var.ecr_repository_arn]
  }

  statement {
    sid       = "TaskDefinitions"
    actions   = ["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"]
    resources = ["*"] # these actions do not support resource-level permissions
  }

  statement {
    sid       = "UpdateService"
    actions   = ["ecs:DescribeServices", "ecs:UpdateService"]
    resources = [var.ecs_service_arn]
  }

  statement {
    sid       = "PassTaskRoles"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.task_execution.arn, aws_iam_role.task.arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}
