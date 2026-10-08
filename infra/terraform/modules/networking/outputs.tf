output "vpc_id" {
  description = "ID of the VPC."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "Public subnets (load balancer)."
  value       = aws_subnet.public[*].id
}

output "app_subnet_ids" {
  description = "Private application subnets (ECS tasks)."
  value       = aws_subnet.app[*].id
}

output "data_subnet_ids" {
  description = "Isolated data subnets (RDS)."
  value       = aws_subnet.data[*].id
}

output "nat_gateway_enabled" {
  description = "Whether private subnets have outbound internet access through a NAT gateway."
  value       = var.enable_nat_gateway
}

output "alb_security_group_id" {
  description = "Security group of the load balancer."
  value       = aws_security_group.alb.id
}

output "ecs_security_group_id" {
  description = "Security group of the ECS tasks."
  value       = aws_security_group.ecs.id
}

output "rds_security_group_id" {
  description = "Security group of the RDS instance."
  value       = aws_security_group.rds.id
}
