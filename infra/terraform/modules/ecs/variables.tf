variable "name" {
  description = "Name prefix for ECS resources (e.g. control-center-dev)."
  type        = string
}

variable "aws_region" {
  description = "Region of the CloudWatch log group (used by the awslogs driver)."
  type        = string
}

variable "container_name" {
  description = "Name of the application container in the task definition. The deploy workflow references it."
  type        = string
  default     = "app"
}

variable "container_image" {
  description = "Initial image (repository_url:tag). Later deployments are performed by GitHub Actions, which registers new task definition revisions."
  type        = string
}

variable "container_port" {
  description = "Port the application listens on."
  type        = number
  default     = 8080
}

variable "cpu" {
  description = "Fargate task CPU units (256, 512, 1024, ...)."
  type        = number
  default     = 512
}

variable "memory" {
  description = "Fargate task memory in MiB. Must be a valid combination with cpu."
  type        = number
  default     = 1024
}

variable "cpu_architecture" {
  description = "CPU architecture of the task (X86_64 or ARM64). Must match the image built by CI."
  type        = string
  default     = "X86_64"

  validation {
    condition     = contains(["X86_64", "ARM64"], var.cpu_architecture)
    error_message = "cpu_architecture must be X86_64 or ARM64."
  }
}

variable "desired_count" {
  description = "Number of running tasks. Use at least 2 in production for availability during deployments and AZ failures."
  type        = number
  default     = 1
}

variable "subnet_ids" {
  description = "Subnets the tasks are launched into."
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups attached to the task ENIs."
  type        = list(string)
}

variable "assign_public_ip" {
  description = "Assign a public IP to tasks. Required only when tasks run in public subnets without a NAT gateway."
  type        = bool
  default     = false
}

variable "target_group_arn" {
  description = "ALB target group that receives the container port."
  type        = string
}

variable "execution_role_arn" {
  description = "ECS task execution role ARN."
  type        = string
}

variable "task_role_arn" {
  description = "ECS task role ARN."
  type        = string
}

variable "environment_variables" {
  description = "Plain (non-secret) environment variables passed to the container."
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "Secret environment variables: name => Secrets Manager valueFrom reference (ARN, optionally with :json-key::)."
  type        = map(string)
  default     = {}
}

variable "log_retention_days" {
  description = "Retention of the application log group."
  type        = number
  default     = 30
}

variable "container_insights" {
  description = "Enable CloudWatch Container Insights on the cluster (additional CloudWatch cost)."
  type        = bool
  default     = false
}

variable "health_check_grace_period_seconds" {
  description = "Time ECS ignores failing ALB health checks after a task starts (covers JVM startup and Flyway migrations)."
  type        = number
  default     = 90
}

variable "enable_execute_command" {
  description = "Enable ECS Exec on the service."
  type        = bool
  default     = false
}
