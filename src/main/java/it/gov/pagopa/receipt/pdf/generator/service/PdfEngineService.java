package it.gov.pagopa.receipt.pdf.generator.service;

import it.gov.pagopa.receipt.pdf.generator.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.generator.exception.PDFReceiptGenerationException;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.model.template.ReceiptPDFTemplate;

import java.nio.file.Path;

public interface PdfEngineService {

    /**
     * Build the request and invoke the PDF Engine through {@link PdfEngineClient}
     *
     * @param template       template data for PDF generation
     * @param workingDirPath path to the temp folder used to store the generated PDF
     * @return the result of the invocation
     * @throws PDFReceiptGenerationException when an error occur while invoking the PDF Engine
     */
    PdfEngineResponse generatePDFReceipt(
            ReceiptPDFTemplate template,
            Path workingDirPath
    ) throws PDFReceiptGenerationException;
}
