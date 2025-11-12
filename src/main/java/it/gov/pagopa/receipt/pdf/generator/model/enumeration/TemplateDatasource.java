package it.gov.pagopa.receipt.pdf.generator.model.enumeration;

import lombok.Getter;

/**
 * Enumeration that contains the possible datasource for building the PDF template
 */
@Getter
public enum TemplateDatasource {
    BIZ_EVENT("bizEvent"),
    RECEIPT("receipt"),
    CART("cartForReceipt"),
    PSP_STATIC_CONFIG("pspStaticConfig");

    private final String name;

    TemplateDatasource(String name) {
        this.name = name;
    }
}
