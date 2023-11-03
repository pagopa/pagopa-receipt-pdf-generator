package it.gov.pagopa.receipt.pdf.generator.model.tokenizer;

import lombok.Builder;
import lombok.Data;

/**
 * Model class for the details of invalid param error
 */
@Data
@Builder
public class InvalidParam {

    private String name;
    private String reason;
}
