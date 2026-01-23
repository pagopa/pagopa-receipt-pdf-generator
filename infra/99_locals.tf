locals {
  product = "${var.prefix}-${var.env_short}"
  project = "${var.prefix}-${var.env_short}-${var.location_short}-${var.domain}"

  apim = {
    name       = "${local.product}-apim"
    rg         = "${local.product}-api-rg"
    helpdesk_api_product_id = "technical_support_api"
  }

  receipt_pdf_generator_hostname = var.env == "prod" ? "weu${var.env}.receipt-pdf-generator.internal.platform.pagopa.it" : "weu${var.env}.receipt-pdf-generator.internal.${var.env}.platform.pagopa.it"
  receipt_pdf_generator_url      = "https://${local.receipt_pdf_generator_hostname}/pagopa-receipt-pdf-generator"

  helpdesk_api = {
    display_name          = "Receipt PDF generator - Helpdesk API"
    description           = "Receipt PDF generator API for helpdesk support"
    published             = true
    subscription_required = true
    approval_required     = false
    subscriptions_limit   = 1000
    service_url           = local.receipt_pdf_generator_url
    path                  = "receipts/helpdesk/generator"
  }
}
