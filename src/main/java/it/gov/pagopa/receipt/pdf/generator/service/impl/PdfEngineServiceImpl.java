package it.gov.pagopa.receipt.pdf.generator.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.generator.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.exception.GeneratePDFException;
import it.gov.pagopa.receipt.pdf.generator.exception.PDFReceiptGenerationException;
import it.gov.pagopa.receipt.pdf.generator.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.model.template.ReceiptPDFTemplate;
import it.gov.pagopa.receipt.pdf.generator.service.PdfEngineService;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import org.apache.http.HttpStatus;

import java.net.URL;
import java.nio.file.Path;

import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.ZIP_FILE_NAME;

public class PdfEngineServiceImpl implements PdfEngineService {

    private final PdfEngineClient pdfEngineClient;

    public PdfEngineServiceImpl() {
        this.pdfEngineClient = PdfEngineClientImpl.getInstance();
    }

    PdfEngineServiceImpl(PdfEngineClient pdfEngineClient) {
        this.pdfEngineClient = pdfEngineClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PdfEngineResponse generatePDFReceipt(
            ReceiptPDFTemplate template,
            Path workingDirPath
    ) throws PDFReceiptGenerationException {
        PdfEngineRequest request = new PdfEngineRequest();

        URL templateStream = GenerateCartReceiptPdfServiceImpl.class.getClassLoader().getResource(ZIP_FILE_NAME);
        //Build the request
        request.setTemplate(templateStream);
        request.setData(parseTemplateDataToString(template));
        request.setApplySignature(false);

        PdfEngineResponse pdfEngineResponse = this.pdfEngineClient.generatePDF(request, workingDirPath);

        if (pdfEngineResponse.getStatusCode() != HttpStatus.SC_OK) {
            String errMsg = String.format("PDF-Engine response KO (%s): %s", pdfEngineResponse.getStatusCode(), pdfEngineResponse.getErrorMessage());
            throw new GeneratePDFException(errMsg, pdfEngineResponse.getStatusCode());
        }

        return pdfEngineResponse;
    }


    private String parseTemplateDataToString(ReceiptPDFTemplate template) throws GeneratePDFException {
        try {
            return ObjectMapperUtils.writeValueAsString(template);
        } catch (JsonProcessingException e) {
            throw new GeneratePDFException("Error preparing input data for receipt PDF template", ReasonErrorCode.ERROR_PDF_ENGINE.getCode(), e);
        }
    }
}
