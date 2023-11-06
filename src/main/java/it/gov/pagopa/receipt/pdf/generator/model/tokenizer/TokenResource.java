package it.gov.pagopa.receipt.pdf.generator.model.tokenizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class that hold the token related to a PII
 *
 * //TODO Unused, needed when the BizEvent will be adequately tokenized
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResource {

    private String token;
}
