output "alarm_names" {
  description = "Names of all CloudWatch alarms of the environment."
  value = [
    aws_cloudwatch_metric_alarm.ecs_cpu_high.alarm_name,
    aws_cloudwatch_metric_alarm.ecs_memory_high.alarm_name,
    aws_cloudwatch_metric_alarm.unhealthy_targets.alarm_name,
    aws_cloudwatch_metric_alarm.no_healthy_targets.alarm_name,
    aws_cloudwatch_metric_alarm.target_5xx.alarm_name,
    aws_cloudwatch_metric_alarm.error_logs.alarm_name,
    aws_cloudwatch_metric_alarm.db_cpu_high.alarm_name,
    aws_cloudwatch_metric_alarm.db_free_storage_low.alarm_name,
  ]
}

output "dashboard_url" {
  description = "Console URL of the CloudWatch dashboard."
  value       = "https://${var.aws_region}.console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards/dashboard/${aws_cloudwatch_dashboard.this.dashboard_name}"
}
