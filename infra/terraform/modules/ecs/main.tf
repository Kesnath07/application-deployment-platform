resource "aws_ecs_cluster" "this" {
  name = "${var.name}-cluster"

  setting {
    name  = "containerInsights"
    value = var.container_insights ? "enabled" : "disabled"
  }
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.name}"
  retention_in_days = var.log_retention_days
}

locals {
  container_definition = {
    name      = var.container_name
    image     = var.container_image
    essential = true

    portMappings = [{
      containerPort = var.container_port
      protocol      = "tcp"
    }]

    environment = [for key in sort(keys(var.environment_variables)) : {
      name  = key
      value = var.environment_variables[key]
    }]

    # Resolved by the ECS agent at container start; values never appear in the task definition.
    secrets = [for key in sort(keys(var.secrets)) : {
      name      = key
      valueFrom = var.secrets[key]
    }]

    # The image runs as an unprivileged user; the root filesystem is read-only and
    # only /tmp (needed by the embedded Tomcat) is writable.
    readonlyRootFilesystem = true
    mountPoints = [{
      sourceVolume  = "tmp"
      containerPath = "/tmp"
      readOnly      = false
    }]

    healthCheck = {
      command     = ["CMD", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:${var.container_port}/api/health"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = var.container_name
      }
    }
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.task_role_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  volume {
    name = "tmp"
  }

  container_definitions = jsonencode([local.container_definition])
}

resource "aws_ecs_service" "app" {
  name                   = "${var.name}-service"
  cluster                = aws_ecs_cluster.this.id
  task_definition        = aws_ecs_task_definition.app.arn
  desired_count          = var.desired_count
  launch_type            = "FARGATE"
  platform_version       = "LATEST"
  enable_execute_command = var.enable_execute_command
  propagate_tags         = "SERVICE"

  # Rolling deployment: start new tasks before stopping old ones, and let ECS roll
  # back automatically when new tasks never become healthy.
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = var.health_check_grace_period_seconds

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = var.security_group_ids
    assign_public_ip = var.assign_public_ip
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = var.container_name
    container_port   = var.container_port
  }

  lifecycle {
    # GitHub Actions owns the running image: each deployment registers a new task
    # definition revision (based on the latest one Terraform produced) and updates
    # the service. Terraform must not revert those deployments.
    ignore_changes = [task_definition]
  }
}
