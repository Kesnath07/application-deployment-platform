output "cluster_name" {
  description = "Name of the ECS cluster."
  value       = aws_ecs_cluster.this.name
}

output "cluster_arn" {
  description = "ARN of the ECS cluster."
  value       = aws_ecs_cluster.this.arn
}

output "service_name" {
  description = "Name of the ECS service."
  value       = aws_ecs_service.app.name
}

output "service_arn" {
  description = "ARN of the ECS service (scopes the deploy role)."
  value       = aws_ecs_service.app.id
}

output "task_definition_family" {
  description = "Task definition family. The deploy workflow downloads the latest revision of it."
  value       = aws_ecs_task_definition.app.family
}

output "container_name" {
  description = "Name of the application container."
  value       = var.container_name
}

output "log_group_name" {
  description = "CloudWatch log group of the application."
  value       = aws_cloudwatch_log_group.app.name
}

output "log_group_arn" {
  description = "ARN of the application log group."
  value       = aws_cloudwatch_log_group.app.arn
}
