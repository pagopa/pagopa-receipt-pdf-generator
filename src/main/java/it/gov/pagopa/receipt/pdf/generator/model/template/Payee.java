package it.gov.pagopa.receipt.pdf.generator.model.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import static com.fasterxml.jackson.annotation.JsonInclude.*;

@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class Payee {

    private String name;
    private String taxCode;

}
