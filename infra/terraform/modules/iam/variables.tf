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
