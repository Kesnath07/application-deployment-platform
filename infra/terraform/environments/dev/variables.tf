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
