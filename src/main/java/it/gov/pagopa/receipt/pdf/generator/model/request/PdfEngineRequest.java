package it.gov.pagopa.receipt.pdf.generator.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Model class for PDF engine request
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PdfEngineRequest {

    private String data;
    private boolean applySignature;
}
