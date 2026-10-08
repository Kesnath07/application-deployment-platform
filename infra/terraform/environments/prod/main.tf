locals {
  name = "${var.project}-${var.environment}"

  # Without a NAT gateway, tasks run in public subnets with a public IP so they can
  # reach ECR, CloudWatch, Secrets Manager and GitHub. Inbound traffic is still only
  # accepted from the load balancer security group.
  task_subnet_ids = var.enable_nat_gateway ? module.networking.app_subnet_ids : module.networking.public_subnet_ids

  db_secret_arn = module.rds.credentials_secret_arn
  secret_arns   = compact([local.db_secret_arn, var.github_token_secret_arn])

  container_secrets = merge(
    {
      DB_USERNAME = "${local.db_secret_arn}:username::"
      DB_PASSWORD = "${local.db_secret_arn}:password::"
    },
    var.github_token_secret_arn == null ? {} : { GITHUB_TOKEN = var.github_token_secret_arn }
  )
}

module "networking" {
  source = "../../modules/networking"

  name               = local.name
  vpc_cidr           = var.vpc_cidr
  enable_nat_gateway = var.enable_nat_gateway
  alb_ingress_cidrs  = var.alb_ingress_cidrs
  enable_https       = var.certificate_arn != null
  app_port           = var.container_port
}

module "ecr" {
  source = "../../modules/ecr"

  repository_name = local.name
  max_image_count = var.ecr_max_image_count
  force_delete    = false
}

module "alb" {
  source = "../../modules/alb"

  name                = local.name
  vpc_id              = module.networking.vpc_id
  subnet_ids          = module.networking.public_subnet_ids
  security_group_ids  = [module.networking.alb_security_group_id]
  target_port         = var.container_port
  certificate_arn     = var.certificate_arn
  deletion_protection = true
}

module "rds" {
  source = "../../modules/rds"

  name                        = local.name
  subnet_ids                  = module.networking.data_subnet_ids
  security_group_ids          = [module.networking.rds_security_group_id]
  instance_class              = var.db_instance_class
  multi_az                    = true
  backup_retention_days       = 14
  deletion_protection         = true
  skip_final_snapshot         = false
  secret_recovery_window_days = 7
  password_version            = var.db_password_version
}

module "iam" {
  source = "../../modules/iam"

  name                   = local.name
  ecr_repository_arn     = module.ecr.repository_arn
  log_group_arn          = module.ecs.log_group_arn
  secret_arns            = local.secret_arns
  enable_execute_command = var.enable_execute_command
}

module "ecs" {
  source = "../../modules/ecs"

  name               = local.name
  aws_region         = var.aws_region
  container_image    = "${module.ecr.repository_url}:${var.initial_image_tag}"
  container_port     = var.container_port
  cpu                = var.task_cpu
  memory             = var.task_memory
  desired_count      = var.desired_count
  subnet_ids         = local.task_subnet_ids
  security_group_ids = [module.networking.ecs_security_group_id]
  assign_public_ip   = !var.enable_nat_gateway
  target_group_arn   = module.alb.target_group_arn
  execution_role_arn = module.iam.task_execution_role_arn
  task_role_arn      = module.iam.task_role_arn
  log_retention_days = 90
  container_insights = true

  enable_execute_command = var.enable_execute_command

  environment_variables = {
    APP_ENVIRONMENT                   = var.environment
    APP_VERSION                       = var.initial_image_tag # overwritten by every CI deployment
    DB_HOST                           = module.rds.address
    DB_PORT                           = tostring(module.rds.port)
    DB_NAME                           = module.rds.database_name
    DB_SSL_MODE                       = "require"
    LOGGING_STRUCTURED_FORMAT_CONSOLE = "ecs" # JSON logs for CloudWatch Logs Insights
    HEALTH_CHECK_ENABLED              = "true"
    GITHUB_DISPATCH_ENABLED           = tostring(var.github_token_secret_arn != null)
  }

  secrets = local.container_secrets
}
