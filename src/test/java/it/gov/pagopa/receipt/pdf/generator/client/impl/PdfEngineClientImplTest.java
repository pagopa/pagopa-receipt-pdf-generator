package it.gov.pagopa.receipt.pdf.generator.client.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.receipt.pdf.generator.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.model.template.ReceiptPDFTemplate;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfEngineClientImplTest {

    private static File tempFile;
    private static File tempDirectory;

    @Mock
    private CloseableHttpClient clientMock;

    @InjectMocks
    private PdfEngineClientImpl sut;

    @Mock
    private CloseableHttpResponse mockResponse;
    @Mock
    private StatusLine mockStatusLine;
    @Mock
    private HttpEntity mockEntity;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        Path basePath = Path.of("src/test/resources");
        tempDirectory = Files.createTempDirectory(basePath, "temp").toFile();
        tempFile = File.createTempFile("output", ".tmp", tempDirectory);
    }

    @AfterEach
    void teardown() throws IOException {
        if (tempDirectory.exists()) {
            FileUtils.deleteDirectory(tempDirectory);
        }
        assertFalse(tempDirectory.exists());
        assertFalse(tempFile.exists());
    }

    @Test
    void testSingleton() {
        PdfEngineClientImpl client1 = Assertions.assertDoesNotThrow(PdfEngineClientImpl::getInstance);
        PdfEngineClientImpl client2 = PdfEngineClientImpl.getInstance();

        assertEquals(client1, client2);
    }

    @Test
    void runOk() throws Exception {
        PdfEngineRequest pdfEngineRequest = buildPdfEngineRequest();

        when(clientMock.execute(any())).thenReturn(mockResponse);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(InputStream.nullInputStream());

        PdfEngineResponse result =
                assertDoesNotThrow(() -> sut.generatePDF(pdfEngineRequest, tempDirectory.toPath()));

        assertNotNull(result.getTempPdfPath());
        assertEquals(HttpStatus.SC_OK, result.getStatusCode());
        File tempPdf = new File(result.getTempPdfPath());
        assertTrue(tempPdf.delete());
    }

    @Test
    void runKoUnauthorized() throws IOException {
        PdfEngineRequest pdfEngineRequest = buildPdfEngineRequest();

        when(clientMock.execute(any())).thenReturn(mockResponse);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_UNAUTHORIZED);
        when(mockResponse.getEntity()).thenReturn(mockEntity);

        PdfEngineResponse result =
                assertDoesNotThrow(() -> sut.generatePDF(pdfEngineRequest, tempDirectory.toPath()));

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertEquals("Unauthorized call to PDF engine function", result.getErrorMessage());

        verify(mockEntity, never()).getContent();
    }

    @Test
    void runKo400() throws IOException {
        PdfEngineRequest pdfEngineRequest = buildPdfEngineRequest();
        String errorMessage = "Invalid request";
        String error400 =
                """
                        {
                          "errorId": "a3779a25-9c8a-4a6f-9272-a052119cfd2e",
                          "httpStatusCode": "BAD_REQUEST",
                          "httpStatusDescription": "Bad Request",
                          "appErrorCode": "PDFE_898",
                          "errors": [
                            {
                              "message": "%s"
                            }
                          ]
                        }
                        """.formatted(errorMessage);

        when(clientMock.execute(any())).thenReturn(mockResponse);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(error400.getBytes()));


        PdfEngineResponse result =
                assertDoesNotThrow(() -> sut.generatePDF(pdfEngineRequest, tempDirectory.toPath()));

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertEquals(errorMessage, result.getErrorMessage());

    }

    @Test
    void runKOMakeCallThrowIOException() throws Exception {
        PdfEngineRequest pdfEngineRequest = buildPdfEngineRequest();

        when(clientMock.execute(any())).thenThrow(IOException.class);

        PdfEngineResponse result =
                assertDoesNotThrow(() -> sut.generatePDF(pdfEngineRequest, tempDirectory.toPath()));

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().startsWith("Exception thrown during pdf generation process:"));

        verify(mockResponse, never()).getStatusLine();
        verify(mockStatusLine, never()).getStatusCode();
        verify(mockResponse, never()).getEntity();
        verify(mockEntity, never()).getContent();
    }

    @Test
    void runKOHandleErrorResponseThrowIOException() throws Exception {
        PdfEngineRequest pdfEngineRequest = buildPdfEngineRequest();

        when(clientMock.execute(any())).thenReturn(mockResponse);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenThrow(IOException.class);

        PdfEngineResponse result =
                assertDoesNotThrow(() -> sut.generatePDF(pdfEngineRequest, tempDirectory.toPath()));

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().startsWith("Exception thrown during pdf generation process:"));
    }

    private PdfEngineRequest buildPdfEngineRequest() throws MalformedURLException, JsonProcessingException {
        return PdfEngineRequest.builder()
                .template(tempFile.toURI().toURL())
                .data(objectMapper.writeValueAsString(new ReceiptPDFTemplate()))
                .build();
    }
}
