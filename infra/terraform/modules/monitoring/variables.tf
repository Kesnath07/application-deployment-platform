variable "name" {
  description = "Name prefix for alarms and the dashboard (e.g. control-center-dev)."
  type        = string
}

variable "aws_region" {
  description = "Region shown in the dashboard widgets."
  type        = string
}

variable "ecs_cluster_name" {
  description = "ECS cluster name (metric dimension)."
  type        = string
}

variable "ecs_service_name" {
  description = "ECS service name (metric dimension)."
  type        = string
}

variable "alb_arn_suffix" {
  description = "ARN suffix of the load balancer (metric dimension)."
  type        = string
}

variable "target_group_arn_suffix" {
  description = "ARN suffix of the target group (metric dimension)."
  type        = string
}

variable "db_instance_identifier" {
  description = "RDS instance identifier (metric dimension)."
  type        = string
}

variable "log_group_name" {
  description = "Application log group used for the error-log metric filter."
  type        = string
}

variable "alarm_actions" {
  description = "ARNs notified when an alarm fires or recovers (e.g. an existing SNS topic). Empty keeps alarms console-only."
  type        = list(string)
  default     = []
}

variable "cpu_threshold_percent" {
  description = "ECS service average CPU utilization that triggers an alarm."
  type        = number
  default     = 80
}

variable "memory_threshold_percent" {
  description = "ECS service average memory utilization that triggers an alarm."
  type        = number
  default     = 80
}

variable "target_5xx_threshold" {
  description = "Number of 5xx responses from targets within 5 minutes that triggers an alarm."
  type        = number
  default     = 10
}

variable "error_log_threshold" {
  description = "Number of ERROR log events within 5 minutes that triggers an alarm."
  type        = number
  default     = 5
}

variable "db_cpu_threshold_percent" {
  description = "RDS CPU utilization that triggers an alarm."
  type        = number
  default     = 80
}

variable "db_free_storage_threshold_bytes" {
  description = "Free RDS storage below which an alarm fires."
  type        = number
  default     = 2147483648 # 2 GiB
}
