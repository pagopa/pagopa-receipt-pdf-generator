package it.gov.pagopa.receipt.pdf.generator.utils;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;

public class ReceiptGeneratorUtils {

    private ReceiptGeneratorUtils() {
    }

    public static String getReceiptEventReference(BizEvent bizEvent) {
        String receiptEventReference = null;

        if (bizEvent != null) {
            receiptEventReference = bizEvent.getId();
        }
        return receiptEventReference;
    }

    public static String getCartReceiptEventReference(BizEvent bizEvent) {
        String receiptEventReference = null;

        if (bizEvent != null && bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null) {
            receiptEventReference = String.valueOf(bizEvent.getTransactionDetails().getTransaction().getTransactionId());
        }
        return receiptEventReference;
    }
}
