package it.gov.pagopa.receipt.pdf.generator.client;

import it.gov.pagopa.receipt.pdf.generator.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;

public interface PdfEngineClient {

    PdfEngineResponse generatePDF(PdfEngineRequest pdfEngineRequest);
}
