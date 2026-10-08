locals {
  name = "${var.project}-${var.environment}"
}

module "networking" {
  source = "../../modules/networking"

  name               = local.name
  vpc_cidr           = var.vpc_cidr
  enable_nat_gateway = var.enable_nat_gateway
  alb_ingress_cidrs  = var.alb_ingress_cidrs
}

module "ecr" {
  source = "../../modules/ecr"

  repository_name = local.name
  max_image_count = var.ecr_max_image_count
  force_delete    = true
}
