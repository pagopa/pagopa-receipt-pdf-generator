package it.gov.pagopa.receipt.pdf.generator.utils;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;

public class ReceiptUtils {

    private ReceiptUtils(){}

    public static String getReceiptEventReference(BizEvent bizEvent, boolean isMultiItem) {
        String receiptEventReference = null;

        if (bizEvent != null) {
            if (isMultiItem && bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null) {
                receiptEventReference = String.valueOf(bizEvent.getTransactionDetails().getTransaction().getTransactionId());
            } else {
                receiptEventReference = bizEvent.getId();
            }
        }
        return receiptEventReference;
    }
}
