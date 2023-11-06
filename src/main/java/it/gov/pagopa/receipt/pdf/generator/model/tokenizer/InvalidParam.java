package it.gov.pagopa.receipt.pdf.generator.model.tokenizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class for the details of invalid param error
 *
 * //TODO Unused, needed when the BizEvent will be adequately tokenized
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvalidParam {

    private String name;
    private String reason;
}
