output "task_execution_role_arn" {
  description = "ARN of the ECS task execution role."
  value       = aws_iam_role.task_execution.arn
}

output "task_role_arn" {
  description = "ARN of the ECS task (application) role."
  value       = aws_iam_role.task.arn
}

output "github_deploy_role_arn" {
  description = "Role assumed by GitHub Actions through OIDC (GitHub environment variable AWS_DEPLOY_ROLE_ARN)."
  value       = aws_iam_role.github_deploy.arn
}
