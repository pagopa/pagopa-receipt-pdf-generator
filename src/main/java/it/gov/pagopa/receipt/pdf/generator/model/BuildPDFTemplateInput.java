package it.gov.pagopa.receipt.pdf.generator.model;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BuildPDFTemplateInput {

    private List<BizEvent> listOfBizEvents;
    private boolean requestedByDebtor;
    private String eventId;
    private String amount;
    private Map<String, CartInfo> cartInfoMap;
}
