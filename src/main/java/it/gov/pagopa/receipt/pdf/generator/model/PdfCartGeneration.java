package it.gov.pagopa.receipt.pdf.generator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Model class for PDF cart generation process's response
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfCartGeneration {

    private Map<String, PdfMetadata> debtorMetadataMap;
    private PdfMetadata payerMetadata;

    public void addDebtorMetadataToMap(String debtorFiscalCode, PdfMetadata debtorMetadata) {
        if (this.debtorMetadataMap == null) {
            this.debtorMetadataMap = new HashMap<>();
        }
        this.debtorMetadataMap.put(debtorFiscalCode, debtorMetadata);
    }
}
