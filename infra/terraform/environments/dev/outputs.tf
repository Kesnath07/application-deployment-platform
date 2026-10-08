output "vpc_id" {
  description = "ID of the environment VPC."
  value       = module.networking.vpc_id
}

output "ecr_repository_url" {
  description = "ECR repository URL. Set as the ECR_REPOSITORY variable in the GitHub environment."
  value       = module.ecr.repository_url
}
