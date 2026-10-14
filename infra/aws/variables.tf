variable "region" { type = string }
variable "owner_cidr" {
  type = string
  validation {
    condition     = can(cidrnetmask(var.owner_cidr)) && endswith(var.owner_cidr, "/32")
    error_message = "Provide only your current public IPv4 /32."
  }
}
variable "credits_verified" {
  type    = bool
  default = false
}
variable "credit_balance_usd" {
  type    = number
  default = 0
}
variable "estimated_demo_cost_usd" {
  type    = number
  default = 0
}
variable "credit_expiry" { type = string }
variable "free_plan_expiry" { type = string }
variable "teardown_at" { type = string }
variable "github_repository" {
  type = string
  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "Use owner/repository."
  }
}
variable "github_oidc_provider_arn" {
  type        = string
  description = "Existing GitHub OIDC provider ARN in this account. Create it once if absent."
}
