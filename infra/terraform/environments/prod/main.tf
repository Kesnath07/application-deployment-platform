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
