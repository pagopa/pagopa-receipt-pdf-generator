package it.gov.pagopa.receipt.pdf.generator.entity.receipt;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    private String subject;
    private String payeeName;
}
