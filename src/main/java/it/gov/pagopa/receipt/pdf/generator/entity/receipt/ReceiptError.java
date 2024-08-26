package it.gov.pagopa.receipt.pdf.generator.entity.receipt;

import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptErrorStatusType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class ReceiptError {

    private String id;
    private String bizEventId;
    private String messagePayload;
    private String messageError;
    private ReceiptErrorStatusType status;

}
