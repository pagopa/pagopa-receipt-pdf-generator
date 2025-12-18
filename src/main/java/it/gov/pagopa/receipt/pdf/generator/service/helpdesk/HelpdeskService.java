package it.gov.pagopa.receipt.pdf.generator.service.helpdesk;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.PDVTokenizerException;

import java.util.List;

public interface HelpdeskService {

    /**
     * Creates a new instance of Receipt, using the tokenizer service to mask the PII, based on
     * the provided BizEvent
     *
     * @param bizEvent instance of BizEvent
     * @return generated instance of Receipt
     */
    Receipt createReceipt(BizEvent bizEvent);

    /**
     * Creates a new instance of Cart Receipt, using the tokenizer service to mask the PII, based on
     * the provided list of BizEvent
     *
     * @param bizEventList list of biz event that compose the cart
     * @return the Cart Receipt
     * @throws PDVTokenizerException when an error occur while tokenizing PII
     * @throws JsonProcessingException when an error occur while parsing PDV Tokenizer response
     */
    CartForReceipt buildCart(List<BizEvent> bizEventList) throws PDVTokenizerException, JsonProcessingException;
}
