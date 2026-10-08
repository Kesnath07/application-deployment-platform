output "address" {
  description = "Hostname of the database (resolvable only inside the VPC)."
  value       = aws_db_instance.this.address
}

output "port" {
  description = "Database port."
  value       = aws_db_instance.this.port
}

output "database_name" {
  description = "Name of the application database."
  value       = aws_db_instance.this.db_name
}

output "instance_identifier" {
  description = "RDS instance identifier (CloudWatch metric dimension)."
  value       = aws_db_instance.this.identifier
}

output "credentials_secret_arn" {
  description = "ARN of the Secrets Manager secret holding the username and password JSON keys."
  value       = aws_secretsmanager_secret.database.arn

  # Consumers (ECS tasks) must not start before the secret has a value.
  depends_on = [aws_secretsmanager_secret_version.database]
}
