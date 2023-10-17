package it.gov.pagopa.receipt.pdf.generator.service;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.exception.TemplateDataMappingException;
import it.gov.pagopa.receipt.pdf.generator.model.template.ReceiptPDFTemplate;

public interface BuildTemplateService {

    /**
     *
     * @param bizEvent Biz-event from queue message
     * @param partialTemplate boolean that indicates the type of template
     * @return {@link ReceiptPDFTemplate} compiled template
     */
    ReceiptPDFTemplate buildTemplate(BizEvent bizEvent, boolean partialTemplate) throws TemplateDataMappingException;

}


