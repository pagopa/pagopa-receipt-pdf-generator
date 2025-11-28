package it.gov.pagopa.receipt.pdf.generator.entity.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MessageData {

    private String id;
    private String subject;
    private String markdown;
}
