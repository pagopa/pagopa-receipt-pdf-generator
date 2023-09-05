package it.gov.pagopa.receipt.pdf.generator.entity.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionPsp {
	private String idChannel;
	private String businessName;
	private String serviceName;
}
