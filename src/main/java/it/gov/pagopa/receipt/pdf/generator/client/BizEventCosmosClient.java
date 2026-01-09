package it.gov.pagopa.receipt.pdf.generator.client;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotFoundException;

import java.util.List;

public interface BizEventCosmosClient {

    /**
     * Retrieve biz-even document from CosmosDB database
     *
     * @param eventId Biz-event id
     * @return biz-event document
     * @throws BizEventNotFoundException in case no biz-event has been found with the given idEvent
     */
    BizEvent getBizEventDocument(String eventId) throws BizEventNotFoundException;

    /**
     * Retrieve all biz-event documents related to a specific cart from CosmosDB database
     *
     * @param transactionId id that identifies the cart
     * @return a list of biz-event document
     */
    List<BizEvent> getAllCartBizEventDocument(String transactionId);
}
