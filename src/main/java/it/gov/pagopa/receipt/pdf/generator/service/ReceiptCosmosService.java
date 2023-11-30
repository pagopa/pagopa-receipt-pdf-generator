package it.gov.pagopa.receipt.pdf.generator.service;

import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;

public interface ReceiptCosmosService {

    /**
     * Recovers a receipt from the CosmosDB by the property eventId
     * @param bizEventId BizEvent id relative to the receipt
     * @return the receipt found
     * @throws ReceiptNotFoundException when no receipt has been found
     */
    Receipt getReceipt(String bizEventId) throws ReceiptNotFoundException;
}
