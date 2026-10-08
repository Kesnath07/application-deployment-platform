# --- Credentials ---------------------------------------------------------------
# The password is generated as an ephemeral value and passed only to write-only
# arguments, so it is never persisted in the Terraform plan or state. ECS reads it
# from Secrets Manager when a task starts.

ephemeral "random_password" "master" {
  length           = 32
  special          = true
  override_special = "!#$%^&*()-_=+[]{}<>:?" # RDS forbids / @ " and spaces
}

resource "aws_secretsmanager_secret" "database" {
  name                    = "${var.name}/database"
  description             = "PostgreSQL credentials of the ${var.name} control center"
  recovery_window_in_days = var.secret_recovery_window_days
}

resource "aws_secretsmanager_secret_version" "database" {
  secret_id = aws_secretsmanager_secret.database.id
  secret_string_wo = jsonencode({
    username = var.master_username
    password = ephemeral.random_password.master.result
  })
  secret_string_wo_version = var.password_version
}

# --- Database ------------------------------------------------------------------

resource "aws_db_subnet_group" "this" {
  name        = "${var.name}-db"
  description = "Isolated data subnets for ${var.name}"
  subnet_ids  = var.subnet_ids
}

resource "aws_db_parameter_group" "this" {
  name        = "${var.name}-postgres${var.engine_version}"
  family      = "postgres${var.engine_version}"
  description = "Hardened parameters for ${var.name}"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000" # log queries slower than 1s
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_db_instance" "this" {
  identifier     = "${var.name}-postgres"
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  db_name             = var.database_name
  username            = var.master_username
  password_wo         = ephemeral.random_password.master.result
  password_wo_version = var.password_version

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = var.security_group_ids
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false
  multi_az               = var.multi_az

  backup_retention_period    = var.backup_retention_days
  backup_window              = "02:00-03:00"
  maintenance_window         = "sun:03:30-sun:04:30"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${var.name}-postgres-final"

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
}
