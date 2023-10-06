package it.gov.pagopa.receipt.pdf.generator.entity.receipt;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IOMessageData {
    private String idMessageDebtor;
    private String idMessagePayer;
}
