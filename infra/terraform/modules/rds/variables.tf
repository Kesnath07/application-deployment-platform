variable "name" {
  description = "Name prefix for database resources (e.g. control-center-dev)."
  type        = string
}

variable "subnet_ids" {
  description = "Isolated data subnets for the DB subnet group (at least two AZs)."
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups attached to the instance."
  type        = list(string)
}

variable "engine_version" {
  description = "PostgreSQL major version. Minor versions are upgraded automatically in the maintenance window."
  type        = string
  default     = "16"
}

variable "instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage" {
  description = "Initial storage in GiB."
  type        = number
  default     = 20
}

variable "max_allocated_storage" {
  description = "Upper bound for storage autoscaling in GiB."
  type        = number
  default     = 100
}

variable "database_name" {
  description = "Name of the application database."
  type        = string
  default     = "controlcenter"
}

variable "master_username" {
  description = "Master user name. The password is generated and stored in Secrets Manager only."
  type        = string
  default     = "controlcenter"
}

variable "password_version" {
  description = "Increment to rotate the master password (a new random password is written to RDS and Secrets Manager)."
  type        = number
  default     = 1
}

variable "multi_az" {
  description = "Run a synchronous standby in a second AZ."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  description = "Days automated backups are kept."
  type        = number
  default     = 7
}

variable "deletion_protection" {
  description = "Prevent the instance from being deleted."
  type        = bool
  default     = true
}

variable "skip_final_snapshot" {
  description = "Skip the final snapshot on deletion (only for disposable environments)."
  type        = bool
  default     = false
}

variable "secret_recovery_window_days" {
  description = "Recovery window of the credentials secret after deletion (0 deletes immediately)."
  type        = number
  default     = 7
}
