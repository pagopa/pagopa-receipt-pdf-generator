package it.gov.pagopa.receipt.pdf.generator.model;

import lombok.*;

/**
 * Model class for PDF metadata from Blob Storage
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfMetadata {

    int statusCode;
    String errorMessage;
    String documentName;
    String documentUrl;
}
