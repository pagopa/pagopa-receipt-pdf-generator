package it.gov.pagopa.receipt.pdf.generator.service.helpdesk;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;

public interface HelpdeskService {

    /**
     * Creates a new instance of Receipt, using the tokenizer service to mask the PII, based on
     * the provided BizEvent
     *
     * @param bizEvent instance of BizEvent
     * @return generated instance of Receipt
     */
    Receipt createReceipt(BizEvent bizEvent);
}
