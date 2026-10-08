variable "name" {
  description = "Name prefix for all networking resources (e.g. control-center-dev)."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block of the VPC. Each subnet tier receives /24 blocks carved out of it."
  type        = string

  validation {
    condition     = can(cidrhost(var.vpc_cidr, 0)) && tonumber(split("/", var.vpc_cidr)[1]) <= 16
    error_message = "vpc_cidr must be a valid IPv4 CIDR block of size /16 or larger."
  }
}

variable "az_count" {
  description = "Number of availability zones to spread subnets across (ALB and RDS subnet groups need at least 2)."
  type        = number
  default     = 2

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 3
    error_message = "az_count must be 2 or 3."
  }
}

variable "enable_nat_gateway" {
  description = "Create a single NAT gateway so ECS tasks can run in private subnets. When false, tasks run in public subnets with a public IP (lower cost, still only reachable through the ALB security group)."
  type        = bool
  default     = true
}

variable "app_port" {
  description = "Container port the application listens on."
  type        = number
  default     = 8080
}

variable "db_port" {
  description = "PostgreSQL port."
  type        = number
  default     = 5432
}

variable "alb_ingress_cidrs" {
  description = "CIDR blocks allowed to reach the public load balancer. Restrict to office/VPN ranges for an internal tool."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "enable_https" {
  description = "Open port 443 on the load balancer security group (set when an ACM certificate is configured)."
  type        = bool
  default     = false
}
