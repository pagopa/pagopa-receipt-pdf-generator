package it.gov.pagopa.receipt.pdf.generator.client;

import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;

public interface ReceiptCosmosClient {

    Receipt getReceiptDocument(String receiptId) throws ReceiptNotFoundException;
}
