resource "github_repository_environment" "github_repository_environment" {
  environment = var.env
  repository  = local.github.repository
  # filter teams reviewers from github_organization_teams
  # if reviewers_teams is null no reviewers will be configured for environment
  dynamic "reviewers" {
    for_each = (var.github_repository_environment.reviewers_teams == null || var.env_short != "p" ? [] : [1])
    content {
      teams = matchkeys(
        data.github_organization_teams.all.teams.*.id,
        data.github_organization_teams.all.teams.*.name,
        var.github_repository_environment.reviewers_teams
      )
    }
  }
  deployment_branch_policy {
    protected_branches     = var.github_repository_environment.protected_branches
    custom_branch_policies = var.github_repository_environment.custom_branch_policies
  }
}

locals {
  env_secrets = {
    "CLIENT_ID" : data.azurerm_user_assigned_identity.identity_cd_01.client_id,
    "TENANT_ID" : data.azurerm_client_config.current.tenant_id,
    "SUBSCRIPTION_ID" : data.azurerm_subscription.current.subscription_id,
    "RECEIPTS_STORAGE_CONN_STRING" : data.azurerm_storage_account.receipts_sa.primary_connection_string,
    "RECEIPTS_COSMOS_CONN_STRING" : "AccountEndpoint=https://pagopa-${var.env_short}-${local.location_short}-${local.domain}-ds-cosmos-account.documents.azure.com:443/;AccountKey=${data.azurerm_cosmosdb_account.receipts_cosmos.primary_key};",
    "BIZ_COSMOS_CONN_STRING" : "AccountEndpoint=https://pagopa-${var.env_short}-${local.location_short}-${local.domain}-ds-cosmos-account.documents.azure.com:443/;AccountKey=${data.azurerm_cosmosdb_account.biz_cosmos.primary_key};",
    "RECEIPTS_COSMOS_ENDPOINT" : "https://pagopa-${var.env_short}-${local.location_short}-${local.domain}-ds-cosmos-account.documents.azure.com:443",
    "BIZ_COSMOS_ENDPOINT" : "https://pagopa-${var.env_short}-${local.location_short}-${local.domain}-ds-cosmos-account.documents.azure.com:443",
    "RECEIPTS_COSMOS_KEY": "${data.azurerm_cosmosdb_account.receipts_cosmos.primary_readonly_key}",
    "BIZ_COSMOS_KEY": "${data.azurerm_cosmosdb_account.biz_cosmos.primary_readonly_key}",
    "RECEIPTS_COSMOS_TIMEOUT": var.receipt_cosmos_timeout,
    "BIZ_COSMOS_TIMEOUT": var.biz_cosmos_timeout,
    "AES_SALT": data.azurerm_key_vault_secret.key_vault_integration_test_aes_salt.value,
    "AES_SECRET_KEY": data.azurerm_key_vault_secret.key_vault_integration_test_aes_key.value,
  }
  env_variables = {
    "CONTAINER_APP_ENVIRONMENT_NAME" : local.container_app_environment.name,
    "CONTAINER_APP_ENVIRONMENT_RESOURCE_GROUP_NAME" : local.container_app_environment.resource_group,
    "CLUSTER_NAME" : local.aks_cluster.name,
    "CLUSTER_RESOURCE_GROUP" : local.aks_cluster.resource_group_name,
    "DOMAIN" : local.domain,
    "NAMESPACE" : local.domain,
    "WORKLOAD_IDENTITY_ID": data.azurerm_user_assigned_identity.workload_identity_clientid.client_id
  }
}

###############
# ENV Secrets #
###############

resource "github_actions_environment_secret" "github_environment_runner_secrets" {
  for_each        = local.env_secrets
  repository      = local.github.repository
  environment     = var.env
  secret_name     = each.key
  plaintext_value = each.value
}

#################
# ENV Variables #
#################


resource "github_actions_environment_variable" "github_environment_runner_variables" {
  for_each      = local.env_variables
  repository    = local.github.repository
  environment   = var.env
  variable_name = each.key
  value         = each.value
}

#############################
# Secrets of the Repository #
#############################

 #tfsec:ignore:github-actions-no-plain-text-action-secrets # not real secret
 resource "github_actions_secret" "secret_sonar_token" {
   repository       = local.github.repository
   secret_name      = "SONAR_TOKEN"
   plaintext_value  = data.azurerm_key_vault_secret.key_vault_sonar.value
 }

 #tfsec:ignore:github-actions-no-plain-text-action-secrets # not real secret
 resource "github_actions_secret" "secret_bot_token" {

   repository       = local.github.repository
   secret_name      = "BOT_TOKEN_GITHUB"
   plaintext_value  = data.azurerm_key_vault_secret.key_vault_bot_token.value
 }

 #tfsec:ignore:github-actions-no-plain-text-action-secrets # not real secret
 resource "github_actions_secret" "secret_cucumber_token" {

   repository       = local.github.repository
   secret_name      = "CUCUMBER_PUBLISH_TOKEN"
   plaintext_value  = data.azurerm_key_vault_secret.key_vault_cucumber_token.value
 }

#tfsec:ignore:github-actions-no-plain-text-action-secrets # not real secret
resource "github_actions_secret" "secret_slack_webhook" {

  repository      = local.github.repository
  secret_name     = "SLACK_WEBHOOK_URL"
  plaintext_value = data.azurerm_key_vault_secret.key_vault_integration_test_webhook_slack.value
}
