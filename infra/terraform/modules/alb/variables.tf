variable "name" {
  description = "Name prefix for load balancer resources. ALB and target group names are limited to 32 characters."
  type        = string

  validation {
    condition     = length(var.name) <= 29
    error_message = "name must be at most 29 characters so that '<name>-tg' fits the 32 character limit."
  }
}

variable "vpc_id" {
  description = "VPC of the target group."
  type        = string
}

variable "subnet_ids" {
  description = "Public subnets for the load balancer (at least two AZs)."
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups attached to the load balancer."
  type        = list(string)
}

variable "target_port" {
  description = "Container port traffic is forwarded to."
  type        = number
  default     = 8080
}

variable "health_check_path" {
  description = "Path used by the target group health check."
  type        = string
  default     = "/api/health"
}

variable "certificate_arn" {
  description = "ACM certificate for the HTTPS listener. When null, the ALB serves plain HTTP only (suitable for dev)."
  type        = string
  default     = null
}

variable "deletion_protection" {
  description = "Protect the load balancer from accidental deletion."
  type        = bool
  default     = false
}
