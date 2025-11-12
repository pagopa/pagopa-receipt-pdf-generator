package it.gov.pagopa.receipt.pdf.generator.service.impl;

import it.gov.pagopa.receipt.pdf.generator.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.generator.exception.GeneratePDFException;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class PdfEngineServiceImplTest {

    private static final String PDF_PATH = "pdfPath";

    @Mock
    private PdfEngineClient pdfEngineClientMock;

    @InjectMocks
    private PdfEngineServiceImpl sut;

    @Test
    void generatePDFReceiptSuccess() {
        doReturn(getPdfEngineResponse(HttpStatus.SC_OK, PDF_PATH))
                .when(pdfEngineClientMock).generatePDF(any(), any());

        PdfEngineResponse result = assertDoesNotThrow(() -> sut.generatePDFReceipt(any(), any()));

        assertNotNull(result);
        assertEquals(PDF_PATH, result.getTempPdfPath());
        assertEquals(HttpStatus.SC_OK, result.getStatusCode());
    }

    @Test
    void generatePDFReceiptFailOnParse() {
        doReturn(getPdfEngineResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, null))
                .when(pdfEngineClientMock).generatePDF(any(), any());

        GeneratePDFException e = assertThrows(GeneratePDFException.class, () -> sut.generatePDFReceipt(any(), any()));

        assertNotNull(e);
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getStatusCode());
    }

    private PdfEngineResponse getPdfEngineResponse(int status, String pdfPath) {
        PdfEngineResponse pdfEngineResponse = new PdfEngineResponse();
        pdfEngineResponse.setTempPdfPath(pdfPath);
        if (status != HttpStatus.SC_OK) {
            pdfEngineResponse.setErrorMessage("error");
        }
        pdfEngineResponse.setStatusCode(status);
        return pdfEngineResponse;
    }

}