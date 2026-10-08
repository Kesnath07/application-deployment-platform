output "dns_name" {
  description = "Public DNS name of the load balancer."
  value       = aws_lb.this.dns_name
}

output "url" {
  description = "Base URL of the application."
  value       = "${local.https_enabled ? "https" : "http"}://${aws_lb.this.dns_name}"
}

output "arn_suffix" {
  description = "ARN suffix of the load balancer (CloudWatch metric dimension)."
  value       = aws_lb.this.arn_suffix
}

output "target_group_arn" {
  description = "ARN of the application target group."
  value       = aws_lb_target_group.app.arn
}

output "target_group_arn_suffix" {
  description = "ARN suffix of the target group (CloudWatch metric dimension)."
  value       = aws_lb_target_group.app.arn_suffix
}
