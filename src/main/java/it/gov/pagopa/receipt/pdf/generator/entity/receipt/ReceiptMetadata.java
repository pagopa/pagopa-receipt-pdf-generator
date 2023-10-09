package it.gov.pagopa.receipt.pdf.generator.entity.receipt;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptMetadata {

    private String name;
    private String url;
}
