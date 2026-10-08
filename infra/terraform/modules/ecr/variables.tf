variable "repository_name" {
  description = "Name of the ECR repository."
  type        = string
}

variable "max_image_count" {
  description = "Number of most recent images to keep. Older images are expired (keep enough for rollbacks)."
  type        = number
  default     = 30
}

variable "untagged_image_expiry_days" {
  description = "Days after which untagged images (e.g. interrupted pushes) are removed."
  type        = number
  default     = 7
}

variable "force_delete" {
  description = "Allow terraform destroy to delete the repository even if it still contains images."
  type        = bool
  default     = false
}
