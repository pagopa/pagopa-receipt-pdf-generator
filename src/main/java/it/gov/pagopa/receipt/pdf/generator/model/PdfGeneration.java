package it.gov.pagopa.receipt.pdf.generator.model;

import lombok.*;

/**
 * Model class for PDF generation process' response
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfGeneration {

    private boolean generateOnlyDebtor;
    private PdfMetadata debtorMetadata;
    private PdfMetadata payerMetadata;

}
