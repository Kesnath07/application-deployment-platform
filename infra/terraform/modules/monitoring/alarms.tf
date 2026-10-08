locals {
  ecs_dimensions = {
    ClusterName = var.ecs_cluster_name
    ServiceName = var.ecs_service_name
  }
  target_dimensions = {
    LoadBalancer = var.alb_arn_suffix
    TargetGroup  = var.target_group_arn_suffix
  }
}

# --- ECS service ---------------------------------------------------------------

resource "aws_cloudwatch_metric_alarm" "ecs_cpu_high" {
  alarm_name          = "${var.name}-ecs-cpu-high"
  alarm_description   = "ECS service CPU above ${var.cpu_threshold_percent}% for 15 minutes"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  dimensions          = local.ecs_dimensions
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = var.cpu_threshold_percent
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_actions
  ok_actions          = var.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "ecs_memory_high" {
  alarm_name          = "${var.name}-ecs-memory-high"
  alarm_description   = "ECS service memory above ${var.memory_threshold_percent}% for 15 minutes"
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  dimensions          = local.ecs_dimensions
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = var.memory_threshold_percent
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_actions
  ok_actions          = var.alarm_actions
}

# --- Load balancer -------------------------------------------------------------

resource "aws_cloudwatch_metric_alarm" "unhealthy_targets" {
  alarm_name          = "${var.name}-alb-unhealthy-targets"
  alarm_description   = "At least one ECS task fails the ALB health check (/api/health)"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  dimensions          = local.target_dimensions
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_actions
  ok_actions          = var.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "no_healthy_targets" {
  alarm_name          = "${var.name}-alb-no-healthy-targets"
  alarm_description   = "The service has no healthy targets: the application is down"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HealthyHostCount"
  dimensions          = local.target_dimensions
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = var.alarm_actions
  ok_actions          = var.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "target_5xx" {
  alarm_name          = "${var.name}-alb-target-5xx"
  alarm_description   = "Application returned more than ${var.target_5xx_threshold} HTTP 5xx responses in 5 minutes"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  dimensions          = local.target_dimensions
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = var.target_5xx_threshold
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_actions
  ok_actions          = var.alarm_actions
}

# --- Application logs ------------------------------------------------------------
# The application writes ECS-formatted JSON logs; count ERROR events as a metric.

resource "aws_cloudwatch_log_metric_filter" "error_logs" {
  name           = "${var.name}-error-logs"
  log_group_name = var.log_group_name
  pattern        = "{ $.log.level = \"ERROR\" }"

  metric_transformation {
    name          = "ErrorLogCount"
    namespace     = "ControlCenter/${var.name}"
    value         = "1"
    default_value = "0"
  }
}

resource "aws_cloudwatch_metric_alarm" "error_logs" {
  alarm_name          = "${var.name}-app-error-logs"
  alarm_description   = "More than ${var.error_log_threshold} ERROR log events in 5 minutes"
  namespace           = "ControlCenter/${var.name}"
  metric_name         = aws_cloudwatch_log_metric_filter.error_logs.metric_transformation[0].name
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = var.error_log_threshold
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_actions
  ok_actions          = var.alarm_actions
}

# --- Database --------------------------------------------------------------------

resource "aws_cloudwatch_metric_alarm" "db_cpu_high" {
  alarm_name          = "${var.name}-rds-cpu-high"
  alarm_description   = "RDS CPU above ${var.db_cpu_threshold_percent}% for 15 minutes"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  dimensions          = { DBInstanceIdentifier = var.db_instance_identifier }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = var.db_cpu_threshold_percent
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_actions
  ok_actions          = var.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "db_free_storage_low" {
  alarm_name          = "${var.name}-rds-free-storage-low"
  alarm_description   = "RDS free storage below ${floor(var.db_free_storage_threshold_bytes / 1073741824)} GiB"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  dimensions          = { DBInstanceIdentifier = var.db_instance_identifier }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 1
  threshold           = var.db_free_storage_threshold_bytes
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_actions
  ok_actions          = var.alarm_actions
}
