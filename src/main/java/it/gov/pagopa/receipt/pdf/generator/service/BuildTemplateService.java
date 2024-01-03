package it.gov.pagopa.receipt.pdf.generator.service;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.TemplateDataMappingException;
import it.gov.pagopa.receipt.pdf.generator.model.template.ReceiptPDFTemplate;

import java.util.List;

public interface BuildTemplateService {

    /**
     * Maps a bizEvent to the json needed to compile the template
     *
     * @param listOfBizEvents List of Biz-events from queue message
     * @param partialTemplate boolean that indicates the type of template
     * @param receipt Receipt from CosmosDB
     * @return {@link ReceiptPDFTemplate} compiled template
     * @throws {@link TemplateDataMappingException} when mandatory fields are missing
     */
    ReceiptPDFTemplate buildTemplate(List<BizEvent> listOfBizEvents, boolean partialTemplate, Receipt receipt) throws TemplateDataMappingException;
}


