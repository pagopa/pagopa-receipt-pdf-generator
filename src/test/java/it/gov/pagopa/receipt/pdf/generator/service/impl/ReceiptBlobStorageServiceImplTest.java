package it.gov.pagopa.receipt.pdf.generator.service.impl;

import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptBlobClient;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.exception.SavePDFToBlobException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.generator.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReceiptBlobStorageServiceImplTest {

    private static final String DOCUMENT = "document";
    private static final String URL = "url";
    private static final String BLOB_NAME = "blob-name";

    private static File outputPdf;
    private static File tempDirectory;

    @Mock
    private ReceiptBlobClient receiptBlobClientMock;

    @InjectMocks
    private ReceiptBlobStorageServiceImpl sut;

    @BeforeEach
    void setUp() throws IOException {
        sut.setMinFileLength(0);
        Path basePath = Path.of("src/test/resources");
        tempDirectory = Files.createTempDirectory(basePath, "temp").toFile();
        outputPdf = File.createTempFile("output", ".tmp", tempDirectory);
    }

    @AfterEach
    void teardown() throws IOException {
        if (tempDirectory.exists()) {
            FileUtils.deleteDirectory(tempDirectory);
        }
        assertFalse(tempDirectory.exists());
        assertFalse(outputPdf.exists());
    }

    @Test
    void saveToBlobStorageSuccess() {
        doReturn(getBlobStorageResponse(HttpStatus.CREATED.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());

        PdfMetadata result = assertDoesNotThrow(() -> sut.saveToBlobStorage(buildPdfEngineResponse(), BLOB_NAME));

        assertNotNull(result);
        assertEquals(DOCUMENT, result.getDocumentName());
        assertEquals(URL, result.getDocumentUrl());
        assertEquals(org.apache.http.HttpStatus.SC_OK, result.getStatusCode());
    }

    @Test
    void saveToBlobStorageFailErrorOnFile() {
        sut.setMinFileLength(10);

        SavePDFToBlobException e = assertThrows(
                SavePDFToBlobException.class,
                () -> sut.saveToBlobStorage(buildPdfEngineResponse(), BLOB_NAME)
        );

        assertNotNull(e);
        assertEquals(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), e.getStatusCode());
        assertNotNull(e.getMessage());

        verify(receiptBlobClientMock, never()).savePdfToBlobStorage(any(), anyString());
    }

    @Test
    void saveToBlobStorageFailErrorOnSave() {
        doThrow(RuntimeException.class)
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());

        SavePDFToBlobException e = assertThrows(
                SavePDFToBlobException.class,
                () -> sut.saveToBlobStorage(buildPdfEngineResponse(), BLOB_NAME)
        );

        assertNotNull(e);
        assertEquals(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), e.getStatusCode());
        assertNotNull(e.getMessage());
    }


    @Test
    void saveToBlobStorageFailResponseNot201() {
        doReturn(getBlobStorageResponse(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());

        SavePDFToBlobException e = assertThrows(
                SavePDFToBlobException.class,
                () -> sut.saveToBlobStorage(buildPdfEngineResponse(), BLOB_NAME)
        );

        assertNotNull(e);
        assertEquals(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), e.getStatusCode());
        assertNotNull(e.getMessage());
    }

    private BlobStorageResponse getBlobStorageResponse(int status) {
        BlobStorageResponse blobStorageResponse = new BlobStorageResponse();
        blobStorageResponse.setStatusCode(status);
        if (status == com.microsoft.azure.functions.HttpStatus.CREATED.value()) {
            blobStorageResponse.setDocumentName(DOCUMENT);
            blobStorageResponse.setDocumentUrl(URL);
        }
        return blobStorageResponse;
    }

    private PdfEngineResponse buildPdfEngineResponse() {
        PdfEngineResponse pdfEngineResponse = new PdfEngineResponse();
        pdfEngineResponse.setTempPdfPath(outputPdf.getPath());
        pdfEngineResponse.setStatusCode(org.apache.http.HttpStatus.SC_OK);
        return pdfEngineResponse;
    }

}