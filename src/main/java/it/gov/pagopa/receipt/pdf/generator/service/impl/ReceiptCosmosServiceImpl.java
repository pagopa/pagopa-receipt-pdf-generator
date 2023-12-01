package it.gov.pagopa.receipt.pdf.generator.service.impl;

import com.azure.cosmos.models.CosmosItemResponse;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToSaveException;
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptCosmosService;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReceiptCosmosServiceImpl implements ReceiptCosmosService {
    private final Logger logger = LoggerFactory.getLogger(ReceiptCosmosServiceImpl.class);
    private final ReceiptCosmosClient receiptCosmosClient;

    public ReceiptCosmosServiceImpl() {
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
    }

    public ReceiptCosmosServiceImpl(ReceiptCosmosClient receiptCosmosClient) {
        this.receiptCosmosClient = receiptCosmosClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Receipt getReceipt(String bizEventId) throws ReceiptNotFoundException {
        Receipt receipt;
        try {
            receipt = receiptCosmosClient.getReceiptDocument(bizEventId);
        } catch (ReceiptNotFoundException e) {
            String errorMsg = String.format("Receipt not found with the biz-event id %s", bizEventId);
            throw new ReceiptNotFoundException(errorMsg, e);
        }

        if (receipt == null) {
            String errorMsg = String.format("Receipt retrieved with the biz-event id %s is null", bizEventId);
            throw new ReceiptNotFoundException(errorMsg);
        }
        return receipt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveReceipt(Receipt receipt) throws UnableToSaveException {
        int statusCode;

        try{
            CosmosItemResponse<Receipt> response = receiptCosmosClient.saveReceipts(receipt);
            statusCode = response.getStatusCode();
        }  catch (Exception e) {
            statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
            logger.error(String.format("Save receipt with eventId %s on cosmos failed", receipt.getEventId()), e);
        }

        if(statusCode != com.microsoft.azure.functions.HttpStatus.CREATED.value()){
            String errorMsg = String.format("Save receipt with eventId %s on cosmos failed with status %s", receipt.getEventId(), statusCode);
            throw new UnableToSaveException(errorMsg);
        }
    }
}
