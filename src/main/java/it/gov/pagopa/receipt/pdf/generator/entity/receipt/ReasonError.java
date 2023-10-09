package it.gov.pagopa.receipt.pdf.generator.entity.receipt;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReasonError {
    private int code;
    private String message;

}
