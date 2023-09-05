package it.gov.pagopa.receipt.pdf.datastore.entity.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Debtor {
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
