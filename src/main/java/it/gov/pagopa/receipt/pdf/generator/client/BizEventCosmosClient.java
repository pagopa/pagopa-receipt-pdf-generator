package it.gov.pagopa.receipt.pdf.generator.client;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;

public interface BizEventCosmosClient {
    BizEvent getBizEventDocument(String eventId) throws ReceiptNotFoundException, BizEventNotFoundException;

}