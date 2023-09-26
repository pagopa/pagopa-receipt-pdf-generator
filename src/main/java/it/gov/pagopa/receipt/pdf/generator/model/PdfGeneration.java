package it.gov.pagopa.receipt.pdf.generator.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Model class for PDF generation process' response
 */
@Getter
@Setter
@NoArgsConstructor
public class PdfGeneration {

    private boolean generateOnlyDebtor;
    private PdfMetadata debtorMetadata;
    private PdfMetadata payerMetadata;

}
