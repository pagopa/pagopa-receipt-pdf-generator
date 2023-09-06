package it.gov.pagopa.receipt.pdf.generator.entity.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payer {
	private String fullName;
	private String entityUniqueIdentifierType;
	private String entityUniqueIdentifierValue;
	private String streetName;
	private String civicNumber;
	private String postalCode;
	private String city;
	private String stateProvinceRegion;
	private String country;
	@JsonProperty(value="eMail")
	private String eMail;
}
