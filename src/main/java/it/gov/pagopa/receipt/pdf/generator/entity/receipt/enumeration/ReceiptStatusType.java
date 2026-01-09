package it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration;

import java.util.Set;

public enum ReceiptStatusType {
    NOT_QUEUE_SENT, INSERTED, RETRY, GENERATED, SIGNED, FAILED, IO_NOTIFIED, IO_ERROR_TO_NOTIFY, IO_NOTIFIER_RETRY, UNABLE_TO_SEND, NOT_TO_NOTIFY, TO_REVIEW;

    private static final Set<ReceiptStatusType> NOTIFIER_STATUS = Set.of(
            IO_NOTIFIED,
            IO_ERROR_TO_NOTIFY,
            IO_NOTIFIER_RETRY,
            NOT_TO_NOTIFY
    );

    public boolean isANotifierStatus() {
        return NOTIFIER_STATUS.contains(this);
    }
}
