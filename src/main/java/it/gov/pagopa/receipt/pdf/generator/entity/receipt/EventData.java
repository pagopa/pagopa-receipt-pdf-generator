package it.gov.pagopa.receipt.pdf.generator.entity.receipt;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventData {
    private String payerFiscalCode;
    private String debtorFiscalCode;
    private String transactionCreationDate;
    private String amount;
    private List<CartItem> cart;
}
