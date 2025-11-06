package it.gov.pagopa.receipt.pdf.generator.service;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.TemplateDataMappingException;
import it.gov.pagopa.receipt.pdf.generator.model.CartInfo;
import it.gov.pagopa.receipt.pdf.generator.model.template.ReceiptPDFTemplate;

import java.util.List;
import java.util.Map;

public interface BuildTemplateService {

    /**
     * Maps a bizEvent to the json needed to compile the template
     *
     * @param bizEvent        Biz-event from queue message
     * @param partialTemplate boolean that indicates the type of template
     * @param receipt         Receipt from CosmosDB
     * @return {@link ReceiptPDFTemplate} compiled template
     * @throws TemplateDataMappingException when mandatory fields are missing
     */
    ReceiptPDFTemplate buildTemplate(
            BizEvent bizEvent,
            boolean partialTemplate,
            Receipt receipt
    ) throws TemplateDataMappingException;

    /**
     * Maps a list of bizEvents to the json needed to compile the template for cart receipts
     *
     * @param listOfBizEvents   List of Biz-events from queue message
     * @param requestedByDebtor boolean that indicates the type of template
     * @param eventId           the cart identifier
     * @param amount            the total amount of the cart
     * @param cartInfoMap       map that contains info about the cart items
     * @return {@link ReceiptPDFTemplate} compiled template
     * @throws TemplateDataMappingException when mandatory fields are missing
     */
    ReceiptPDFTemplate buildCartTemplate(
            List<BizEvent> listOfBizEvents,
            boolean requestedByDebtor,
            String eventId,
            String amount,
            Map<String, CartInfo> cartInfoMap
    ) throws TemplateDataMappingException;
}


