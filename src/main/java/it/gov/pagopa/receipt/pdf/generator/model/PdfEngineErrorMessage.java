package it.gov.pagopa.receipt.pdf.generator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

/**
 * Model class for PDF engine HTTP error messages
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PdfEngineErrorMessage {
    private String message;
}
