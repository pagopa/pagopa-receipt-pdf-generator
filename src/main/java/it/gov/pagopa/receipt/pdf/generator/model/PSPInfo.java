package it.gov.pagopa.receipt.pdf.generator.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PSPInfo {
    private String logo;
    private String address;
    private String buildingNumber;
    private String postalCode;
    private String city;
    private String province;
}
