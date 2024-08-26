package it.gov.pagopa.receipt.pdf.generator.entity.receipt;

import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptErrorStatusType;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class ReceiptError {

    @Builder.Default
    private String id = UUID.randomUUID().toString();
    private String bizEventId;
    private String messagePayload;
    private String messageError;
    private ReceiptErrorStatusType status;

}
