package it.gov.pagopa.receipt.pdf.generator.service;

import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToSaveException;

public interface ReceiptCosmosService {

    /**
     * Recovers a receipt from the CosmosDB by the property eventId
     * @param bizEventId BizEvent id relative to the receipt
     * @return the receipt found
     * @throws ReceiptNotFoundException when no receipt has been found
     */
    Receipt getReceipt(String bizEventId) throws ReceiptNotFoundException;

    /**
     * Saves receipts on CosmosDB using {@link ReceiptCosmosClient}
     *
     * @param receipt Receipt to save
     */
    void updateReceipt(Receipt receipt) throws UnableToSaveException;
}
