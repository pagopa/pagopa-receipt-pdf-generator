locals {
  product = "${var.prefix}-${var.env_short}"
  project = "${var.prefix}-${var.env_short}-${var.location_short}-${var.domain}"

  apim = {
    name       = "${local.product}-apim"
    rg         = "${local.product}-api-rg"
    helpdesk_api_product_id = "technical_support_api"
    receipts_internal_api_product_id = "receipts-internal"
  }

  receipt_pdf_generator_hostname = var.env == "prod" ? "weu${var.env}.receipts.internal.platform.pagopa.it" : "weu${var.env}.receipts.internal.${var.env}.platform.pagopa.it"
  receipt_pdf_generator_url      = "https://${local.receipt_pdf_generator_hostname}/pagopa-receipt-pdf-generator-helpdesk"

  helpdesk_api = {
    display_name          = "Receipt PDF Generator - Helpdesk API"
    description           = "Receipt PDF Generator API for helpdesk support"
    published             = true
    subscription_required = true
    approval_required     = false
    subscriptions_limit   = 1000
    service_url           = local.receipt_pdf_generator_url
    path                  = "receipts/helpdesk/generator"
  }
}
