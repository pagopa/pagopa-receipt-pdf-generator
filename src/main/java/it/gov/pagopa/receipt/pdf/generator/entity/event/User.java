package it.gov.pagopa.receipt.pdf.generator.entity.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import it.gov.pagopa.receipt.pdf.generator.entity.event.enumeration.UserType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {
	private String fullName;
	private UserType type;
	private String fiscalCode;
	private String notificationEmail;
	private String userId;
	private String userStatus;
	private String userStatusDescription;
}
