variable "name" {
  description = "Name prefix for IAM roles (e.g. control-center-dev)."
  type        = string
}

variable "ecr_repository_arn" {
  description = "ARN of the ECR repository the tasks pull their image from."
  type        = string
}

variable "log_group_arn" {
  description = "ARN of the CloudWatch log group the containers write to."
  type        = string
}

variable "secret_arns" {
  description = "Secrets Manager secret ARNs injected into the container at startup (database credentials, optional GitHub token)."
  type        = list(string)
  default     = []
}

variable "enable_execute_command" {
  description = "Grant the task role the permissions required for ECS Exec (interactive debugging)."
  type        = bool
  default     = false
}

# --- GitHub Actions OIDC -------------------------------------------------------

variable "github_repository" {
  description = "Repository (owner/name) whose workflows may assume the deploy role."
  type        = string
}

variable "github_environment" {
  description = "GitHub environment the deploy job must run in. Tokens from other branches, PRs or environments are rejected."
  type        = string
}

variable "create_github_oidc_provider" {
  description = "Create the account-wide GitHub OIDC identity provider. Only one may exist per AWS account: set to false in all but one environment sharing an account."
  type        = bool
  default     = true
}

variable "ecs_service_arn" {
  description = "ARN of the ECS service the deploy role may update."
  type        = string
}
