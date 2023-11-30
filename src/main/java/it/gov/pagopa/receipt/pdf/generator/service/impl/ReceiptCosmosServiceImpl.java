package it.gov.pagopa.receipt.pdf.generator.service.impl;

import it.gov.pagopa.receipt.pdf.generator.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptCosmosService;

public class ReceiptCosmosServiceImpl implements ReceiptCosmosService {

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
            String errorMsg = String.format("Receipt not found with the biz-event id %s",bizEventId);
            throw new ReceiptNotFoundException(errorMsg, e);
        }

        if (receipt == null) {
            String errorMsg = String.format("Receipt retrieved with the biz-event id %s is null", bizEventId);
            throw new ReceiptNotFoundException(errorMsg);
        }
        return receipt;
    }
}
