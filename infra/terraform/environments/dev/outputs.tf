output "vpc_id" {
  description = "ID of the environment VPC."
  value       = module.networking.vpc_id
}
