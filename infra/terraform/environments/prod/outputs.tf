output "vpc_id" {
  description = "ID of the environment VPC."
  value       = module.networking.vpc_id
}

output "alb_url" {
  description = "Public URL of the control center. Use it as the environment URL in the dashboard."
  value       = module.alb.url
}

output "ecr_repository_url" {
  description = "ECR repository URL. Set as the ECR_REPOSITORY variable in the GitHub environment."
  value       = module.ecr.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name (GitHub environment variable ECS_CLUSTER)."
  value       = module.ecs.cluster_name
}

output "ecs_service_name" {
  description = "ECS service name (GitHub environment variable ECS_SERVICE)."
  value       = module.ecs.service_name
}

output "ecs_task_definition_family" {
  description = "Task definition family (GitHub environment variable ECS_TASK_DEFINITION)."
  value       = module.ecs.task_definition_family
}

output "ecs_container_name" {
  description = "Container name inside the task definition (GitHub environment variable ECS_CONTAINER_NAME)."
  value       = module.ecs.container_name
}

output "log_group_name" {
  description = "CloudWatch log group of the application."
  value       = module.ecs.log_group_name
}

output "rds_endpoint" {
  description = "Private database hostname."
  value       = module.rds.address
}

output "db_credentials_secret_arn" {
  description = "Secrets Manager secret with the database credentials."
  value       = module.rds.credentials_secret_arn
}

output "cloudwatch_dashboard_url" {
  description = "CloudWatch dashboard with ECS, ALB, RDS and log metrics."
  value       = module.monitoring.dashboard_url
}

output "cloudwatch_alarms" {
  description = "CloudWatch alarms created for the environment."
  value       = module.monitoring.alarm_names
}
