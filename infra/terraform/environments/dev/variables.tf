variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "eu-central-1"
}

variable "project" {
  description = "Project name used as a prefix for resource names."
  type        = string
  default     = "control-center"
}

variable "environment" {
  description = "Environment name."
  type        = string
  default     = "dev"
}

variable "github_repository" {
  description = "GitHub repository in owner/name form that deploys this environment."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "github_repository must look like owner/name."
  }
}

variable "create_github_oidc_provider" {
  description = "Create the account-wide GitHub OIDC provider. Set to false when another environment in the same AWS account already created it."
  type        = bool
  default     = true
}

# --- Networking --------------------------------------------------------------

variable "vpc_cidr" {
  description = "CIDR block of the environment VPC."
  type        = string
  default     = "10.10.0.0/16"
}

variable "enable_nat_gateway" {
  description = "Run ECS tasks in private subnets behind a NAT gateway (recommended for production)."
  type        = bool
  default     = false
}

variable "alb_ingress_cidrs" {
  description = "CIDR blocks allowed to reach the load balancer."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

# --- Container registry ------------------------------------------------------

variable "ecr_max_image_count" {
  description = "Number of images kept in ECR (bounds how far back a rollback can go)."
  type        = number
  default     = 30
}

variable "certificate_arn" {
  description = "ACM certificate ARN for HTTPS on the load balancer. Leave null to serve HTTP only."
  type        = string
  default     = null
}

# --- Application / ECS -------------------------------------------------------

variable "container_port" {
  description = "Port the Spring Boot application listens on."
  type        = number
  default     = 8080
}

variable "initial_image_tag" {
  description = "Image tag used when Terraform first creates the task definition. Subsequent deployments are made by GitHub Actions."
  type        = string
  default     = "bootstrap"
}

variable "task_cpu" {
  description = "Fargate task CPU units."
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "Fargate task memory (MiB)."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Number of ECS tasks."
  type        = number
  default     = 1
}

variable "enable_execute_command" {
  description = "Enable ECS Exec for interactive debugging."
  type        = bool
  default     = false
}

variable "github_token_secret_arn" {
  description = "Optional ARN of a Secrets Manager secret (plain string) holding a GitHub token with Actions write access. Enables workflow dispatch from the dashboard."
  type        = string
  default     = null
}

# --- Database ----------------------------------------------------------------

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_password_version" {
  description = "Increment to rotate the database password, then force a new ECS deployment."
  type        = number
  default     = 1
}

# --- Monitoring --------------------------------------------------------------

variable "alarm_actions" {
  description = "Optional notification targets for CloudWatch alarms (e.g. an existing SNS topic ARN)."
  type        = list(string)
  default     = []
}
