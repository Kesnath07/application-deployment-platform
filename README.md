# Cloud Deployment Control Center

An internal developer platform (IDP) for registering applications, managing their
`dev` / `prod` environments and driving container deployments to AWS.

The control plane is a Java 21 / Spring Boot application. Workloads are shipped through
GitHub Actions → Docker → Amazon ECR → Amazon ECS Fargate behind an Application Load
Balancer, with Amazon RDS PostgreSQL for state and Amazon CloudWatch for logs and alarms.
All AWS infrastructure is provisioned with Terraform.

> Work in progress — full documentation is added as the project is built.
