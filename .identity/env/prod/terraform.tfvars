prefix    = "pagopa"
env       = "prod"
env_short = "p"

tags = {
  CreatedBy   = "Terraform"
  Environment = "Prod"
  Owner       = "pagoPA"
  Source      = "https://github.com/pagopa/pagopa-receipt-pdf-generator"
  CostCenter  = "TS310 - PAGAMENTI & SERVIZI"
}


github_repository_environment = {
  protected_branches     = false
  custom_branch_policies = true
  reviewers_teams        = ["pagopa-tech"]
}
