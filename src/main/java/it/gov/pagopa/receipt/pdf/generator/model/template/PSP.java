package it.gov.pagopa.receipt.pdf.generator.model.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import static com.fasterxml.jackson.annotation.JsonInclude.*;

@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class PSP {

    private String name;
    private PSPFee fee;
    private String companyName;
    private String logo;
    private String address;
    private String buildingNumber;
    private String postalCode;
    private String city;
    private String province;

}
