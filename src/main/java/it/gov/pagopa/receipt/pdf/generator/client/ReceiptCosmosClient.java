package it.gov.pagopa.receipt.pdf.generator.client;

import com.azure.cosmos.models.CosmosItemResponse;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;

public interface ReceiptCosmosClient {

    /**
     * Retrieve receipt document from CosmosDB database
     *
     * @param eventId Biz-event id
     * @return receipt document
     * @throws ReceiptNotFoundException in case no receipt has been found with the given idEvent
     */
    Receipt getReceiptDocument(String eventId) throws ReceiptNotFoundException;

    /**
     * Save Receipts on CosmosDB database
     *
     * @param receipt Receipts to save
     * @return receipt documents
     */
    CosmosItemResponse<Receipt> updateReceipt(Receipt receipt);
}
