package it.gov.pagopa.receipt.pdf.generator.entity.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Creditor {
	private String idPA;
	private String idBrokerPA;
	private String idStation;
	private String companyName;
	private String officeName;
}
