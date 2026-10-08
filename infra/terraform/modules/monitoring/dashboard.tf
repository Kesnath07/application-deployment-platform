locals {
  ecs_dims = ["ClusterName", var.ecs_cluster_name, "ServiceName", var.ecs_service_name]
  tg_dims  = ["TargetGroup", var.target_group_arn_suffix, "LoadBalancer", var.alb_arn_suffix]
  db_dims  = ["DBInstanceIdentifier", var.db_instance_identifier]

  widgets = [
    { title = "ECS CPU / memory (%)", metrics = [
      concat(["AWS/ECS", "CPUUtilization"], local.ecs_dims),
      concat(["AWS/ECS", "MemoryUtilization"], local.ecs_dims),
    ], stat = "Average" },
    { title = "Requests and target 5xx", metrics = [
      ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", var.alb_arn_suffix],
      concat(["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count"], local.tg_dims),
    ], stat = "Sum" },
    { title = "Target response time (s, p95)", metrics = [
      ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.alb_arn_suffix],
    ], stat = "p95" },
    { title = "Healthy / unhealthy targets", metrics = [
      concat(["AWS/ApplicationELB", "HealthyHostCount"], local.tg_dims),
      concat(["AWS/ApplicationELB", "UnHealthyHostCount"], local.tg_dims),
    ], stat = "Minimum" },
    { title = "RDS CPU (%) and connections", metrics = [
      concat(["AWS/RDS", "CPUUtilization"], local.db_dims),
      concat(["AWS/RDS", "DatabaseConnections"], local.db_dims),
    ], stat = "Average" },
    { title = "Application ERROR logs", metrics = [
      ["ControlCenter/${var.name}", "ErrorLogCount"],
    ], stat = "Sum" },
  ]
}

resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = var.name

  dashboard_body = jsonencode({
    widgets = [for index, widget in local.widgets : {
      type   = "metric"
      x      = (index % 2) * 12
      y      = floor(index / 2) * 6
      width  = 12
      height = 6
      properties = {
        title   = widget.title
        region  = var.aws_region
        metrics = widget.metrics
        stat    = widget.stat
        period  = 300
        view    = "timeSeries"
      }
    }]
  })
}
