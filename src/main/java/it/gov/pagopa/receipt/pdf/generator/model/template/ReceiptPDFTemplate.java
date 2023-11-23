package it.gov.pagopa.receipt.pdf.generator.model.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.fasterxml.jackson.annotation.JsonInclude.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class ReceiptPDFTemplate {

    private String serviceCustomerId;
    private Transaction transaction;
    private User user;
    private Cart cart;

}
