package it.gov.pagopa.receipt.pdf.generator.service.impl;

import it.gov.pagopa.receipt.pdf.generator.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptBlobClient;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartPayment;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.Payload;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.exception.TemplateDataMappingException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfCartGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.model.template.ReceiptPDFTemplate;
import it.gov.pagopa.receipt.pdf.generator.service.BuildTemplateService;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
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
import java.util.ArrayList;
import java.util.List;

import static it.gov.pagopa.receipt.pdf.generator.service.impl.GenerateReceiptPdfServiceImpl.ALREADY_CREATED;
import static it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtilsTest.getBizEventFromFile;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GenerateCartReceiptPdfServiceImplTest {

    private static final String VALID_CF_DEBTOR = "JHNDOE00A01F205N";
    private static final String VALID_CF_PAYER = "PLMGHN00A01F406L";
    private static final String BIZ_EVENT_ID = "062-a330-4210-9c67-465b7d641aVS";
    private static final String DEBTOR_DOCUMENT_NAME = "debtorDocumentName";
    private static final String DEBTOR_DOCUMENT_URL = "debtorDocumentUrl";
    private static final String PAYER_DOCUMENT_NAME = "payerDocumentName";
    private static final String PAYER_DOCUMENT_URL = "payerDocumentUrl";
    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String RECEIPT_METADATA_ORIGINAL_URL = "originalUrl";
    private static final String RECEIPT_METADATA_ORIGINAL_NAME = "originalName";
    private static final Path WORKING_DIR_PATH = Path.of("/tmp");
    private static final String SUBJECT = "subject";
    private static final String CART_ID = "cartId";
    private static final String PAYER_FISCAL_CODE = "payerFiscalCode";
    private static final String DEBTOR_FISCAL_CODE = "debtorFiscalCode";

    private static File outputPdfDebtor;
    private static File tempDirectoryDebtor;
    private static File outputPdfPayer;
    private static File tempDirectoryPayer;

    @Mock
    private PdfEngineClient pdfEngineClientMock;
    @Mock
    private ReceiptBlobClient receiptBlobClientMock;
    @Mock
    private BuildTemplateService buildTemplateServiceMock;
    @InjectMocks
    private GenerateCartReceiptPdfServiceImpl sut;

    @BeforeEach
    void setUp() throws IOException {
        sut.setMinFileLength(0);
        Path basePath = Path.of("src/test/resources");
        tempDirectoryDebtor = Files.createTempDirectory(basePath, "tempDebtor").toFile();
        outputPdfDebtor = File.createTempFile("outputDebtor", ".tmp", tempDirectoryDebtor);
        tempDirectoryPayer = Files.createTempDirectory(basePath, "tempPayer").toFile();
        outputPdfPayer = File.createTempFile("outputPayer", ".tmp", tempDirectoryPayer);
    }

    @AfterEach
    void teardown() throws IOException {
        if (tempDirectoryDebtor.exists()) {
            FileUtils.deleteDirectory(tempDirectoryDebtor);
        }
        if (tempDirectoryPayer.exists()) {
            FileUtils.deleteDirectory(tempDirectoryPayer);
        }
        assertFalse(tempDirectoryDebtor.exists());
        assertFalse(outputPdfDebtor.exists());
        assertFalse(tempDirectoryPayer.exists());
        assertFalse(outputPdfPayer.exists());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsPayerNullSuccess() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse(HttpStatus.SC_OK, outputPdfDebtor.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = getCartForReceipt(
                null,
                DEBTOR_FISCAL_CODE,
                totalNotice,
                false,
                false
        );

        PdfCartGeneration result =
                assertDoesNotThrow(() -> sut.generateCartReceipts(cartForReceipt, bizEventList, WORKING_DIR_PATH));

        assertNotNull(result);
        assertNotNull(result.getDebtorMetadataMap());
        assertEquals(2, result.getDebtorMetadataMap().size());
        result.getDebtorMetadataMap().forEach((key, debtorMetadata) -> {
            assertNull(debtorMetadata.getErrorMessage());
            assertNotNull(debtorMetadata.getDocumentName());
            assertNotNull(debtorMetadata.getDocumentUrl());
            assertEquals(HttpStatus.SC_OK, debtorMetadata.getStatusCode());
        });
        assertNull(result.getPayerMetadata());

        verify(buildTemplateServiceMock, times(totalNotice)).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        verify(pdfEngineClientMock, times(totalNotice)).generatePDF(any(), any());
        verify(receiptBlobClientMock, times(totalNotice)).savePdfToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsSameDebtorPayerSuccess() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse(HttpStatus.SC_OK, outputPdfDebtor.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = getCartForReceipt(
                PAYER_FISCAL_CODE,
                PAYER_FISCAL_CODE,
                totalNotice,
                false,
                false
        );

        PdfCartGeneration result =
                assertDoesNotThrow(() -> sut.generateCartReceipts(cartForReceipt, bizEventList, WORKING_DIR_PATH));

        assertNotNull(result);
        assertNull(result.getDebtorMetadataMap());
        assertNotNull(result.getPayerMetadata());
        assertNull(result.getPayerMetadata().getErrorMessage());
        assertNotNull(result.getPayerMetadata().getDocumentName());
        assertNotNull(result.getPayerMetadata().getDocumentUrl());
        assertEquals(HttpStatus.SC_OK, result.getPayerMetadata().getStatusCode());

        verify(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        verify(pdfEngineClientMock).generatePDF(any(), any());
        verify(receiptBlobClientMock).savePdfToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsDifferentDebtorPayerSuccess() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse(HttpStatus.SC_OK, outputPdfDebtor.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = getCartForReceipt(
                PAYER_FISCAL_CODE,
                DEBTOR_FISCAL_CODE,
                totalNotice,
                false,
                false
        );

        PdfCartGeneration result =
                assertDoesNotThrow(() -> sut.generateCartReceipts(cartForReceipt, bizEventList, WORKING_DIR_PATH));

        assertNotNull(result);
        assertNotNull(result.getDebtorMetadataMap());
        assertEquals(2, result.getDebtorMetadataMap().size());
        result.getDebtorMetadataMap().forEach((key, debtorMetadata) -> {
            assertNull(debtorMetadata.getErrorMessage());
            assertNotNull(debtorMetadata.getDocumentName());
            assertNotNull(debtorMetadata.getDocumentUrl());
            assertEquals(HttpStatus.SC_OK, debtorMetadata.getStatusCode());
        });
        assertNotNull(result.getPayerMetadata());
        assertNull(result.getPayerMetadata().getErrorMessage());
        assertNotNull(result.getPayerMetadata().getDocumentName());
        assertNotNull(result.getPayerMetadata().getDocumentUrl());
        assertEquals(HttpStatus.SC_OK, result.getPayerMetadata().getStatusCode());

        verify(buildTemplateServiceMock, times(totalNotice + 1))
                .buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        verify(pdfEngineClientMock, times(totalNotice + 1))
                .generatePDF(any(), any());
        verify(receiptBlobClientMock, times(totalNotice + 1))
                .savePdfToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsDifferentDebtorPayerAndDebtorAnonimoSuccess() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse(HttpStatus.SC_OK, outputPdfDebtor.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = getCartForReceipt(
                PAYER_FISCAL_CODE,
                "ANONIMO",
                totalNotice,
                false,
                false
        );

        PdfCartGeneration result =
                assertDoesNotThrow(() -> sut.generateCartReceipts(cartForReceipt, bizEventList, WORKING_DIR_PATH));

        assertNotNull(result);
        assertNull(result.getDebtorMetadataMap());
        assertNotNull(result.getPayerMetadata());
        assertNull(result.getPayerMetadata().getErrorMessage());
        assertNotNull(result.getPayerMetadata().getDocumentName());
        assertNotNull(result.getPayerMetadata().getDocumentUrl());
        assertEquals(HttpStatus.SC_OK, result.getPayerMetadata().getStatusCode());

        verify(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        verify(pdfEngineClientMock).generatePDF(any(), any());
        verify(receiptBlobClientMock).savePdfToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsPayerNullAndDebtorReceiptAlreadyCreatedSuccess() {
        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = getCartForReceipt(
                null,
                DEBTOR_FISCAL_CODE,
                totalNotice,
                true,
                false
        );

        PdfCartGeneration result =
                assertDoesNotThrow(() -> sut.generateCartReceipts(cartForReceipt, bizEventList, WORKING_DIR_PATH));

        assertNotNull(result);
        assertNotNull(result.getDebtorMetadataMap());
        assertEquals(2, result.getDebtorMetadataMap().size());
        result.getDebtorMetadataMap().forEach((key, debtorMetadata) -> {
            assertNull(debtorMetadata.getErrorMessage());
            assertNull(debtorMetadata.getDocumentName());
            assertNull(debtorMetadata.getDocumentUrl());
            assertEquals(ALREADY_CREATED, debtorMetadata.getStatusCode());
        });
        assertNull(result.getPayerMetadata());

        verify(buildTemplateServiceMock, never()).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        verify(pdfEngineClientMock, never()).generatePDF(any(), any());
        verify(receiptBlobClientMock, never()).savePdfToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsSameDebtorPayerAndPayerReceiptAlreadyCreatedSuccess() {
        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = getCartForReceipt(
                PAYER_FISCAL_CODE,
                PAYER_FISCAL_CODE,
                totalNotice,
                false,
                true
        );

        PdfCartGeneration result =
                assertDoesNotThrow(() -> sut.generateCartReceipts(cartForReceipt, bizEventList, WORKING_DIR_PATH));

        assertNotNull(result);
        assertNull(result.getDebtorMetadataMap());
        assertNotNull(result.getPayerMetadata());
        assertNull(result.getPayerMetadata().getErrorMessage());
        assertNull(result.getPayerMetadata().getDocumentName());
        assertNull(result.getPayerMetadata().getDocumentUrl());
        assertEquals(ALREADY_CREATED, result.getPayerMetadata().getStatusCode());

        verify(buildTemplateServiceMock, never()).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        verify(pdfEngineClientMock, never()).generatePDF(any(), any());
        verify(receiptBlobClientMock, never()).savePdfToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsDifferentDebtorPayerAndPayerReceiptAlreadyCreatedSuccess() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse(HttpStatus.SC_OK, outputPdfDebtor.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = getCartForReceipt(
                PAYER_FISCAL_CODE,
                DEBTOR_FISCAL_CODE,
                totalNotice,
                false,
                true
        );

        PdfCartGeneration result =
                assertDoesNotThrow(() -> sut.generateCartReceipts(cartForReceipt, bizEventList, WORKING_DIR_PATH));

        assertNotNull(result);
        assertNotNull(result.getDebtorMetadataMap());
        assertEquals(2, result.getDebtorMetadataMap().size());
        result.getDebtorMetadataMap().forEach((key, debtorMetadata) -> {
            assertNull(debtorMetadata.getErrorMessage());
            assertNotNull(debtorMetadata.getDocumentName());
            assertNotNull(debtorMetadata.getDocumentUrl());
            assertEquals(HttpStatus.SC_OK, debtorMetadata.getStatusCode());
        });
        assertNotNull(result.getPayerMetadata());
        assertNull(result.getPayerMetadata().getErrorMessage());
        assertNull(result.getPayerMetadata().getDocumentName());
        assertNull(result.getPayerMetadata().getDocumentUrl());
        assertEquals(ALREADY_CREATED, result.getPayerMetadata().getStatusCode());

        verify(buildTemplateServiceMock, times(totalNotice)).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        verify(pdfEngineClientMock, times(totalNotice)).generatePDF(any(), any());
        verify(receiptBlobClientMock, times(totalNotice)).savePdfToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsPayerNullFailPDFEngineCallReturn500() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(
                getPdfEngineResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, ""),
                getPdfEngineResponse(HttpStatus.SC_OK, outputPdfDebtor.getPath())
        ).when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = getCartForReceipt(
                null,
                DEBTOR_FISCAL_CODE,
                totalNotice,
                false,
                false
        );

        PdfCartGeneration result =
                assertDoesNotThrow(() -> sut.generateCartReceipts(cartForReceipt, bizEventList, WORKING_DIR_PATH));

        assertNotNull(result);
        assertNotNull(result.getDebtorMetadataMap());
        assertEquals(2, result.getDebtorMetadataMap().size());
        result.getDebtorMetadataMap().forEach((key, debtorMetadata) -> {
            if (key.equals(BIZ_EVENT_ID + 0)) {
                assertNotNull(debtorMetadata.getErrorMessage());
                assertNull(debtorMetadata.getDocumentName());
                assertNull(debtorMetadata.getDocumentUrl());
                assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, debtorMetadata.getStatusCode());
            } else if (key.equals(BIZ_EVENT_ID + 1)) {
                assertNull(debtorMetadata.getErrorMessage());
                assertNotNull(debtorMetadata.getDocumentName());
                assertNotNull(debtorMetadata.getDocumentUrl());
                assertEquals(HttpStatus.SC_OK, debtorMetadata.getStatusCode());
            } else {
                fail();
            }
        });
        assertNull(result.getPayerMetadata());

        verify(buildTemplateServiceMock, times(totalNotice)).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        verify(pdfEngineClientMock, times(totalNotice)).generatePDF(any(), any());
        verify(receiptBlobClientMock, times(1)).savePdfToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsPayerNullFailBuildTemplateData() {
        doThrow(new TemplateDataMappingException("error message", ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode()))
                .doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse(HttpStatus.SC_OK, outputPdfDebtor.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = getCartForReceipt(
                null,
                DEBTOR_FISCAL_CODE,
                totalNotice,
                false,
                false
        );

        PdfCartGeneration result =
                assertDoesNotThrow(() -> sut.generateCartReceipts(cartForReceipt, bizEventList, WORKING_DIR_PATH));

        assertNotNull(result);
        assertNotNull(result.getDebtorMetadataMap());
        assertEquals(2, result.getDebtorMetadataMap().size());
        result.getDebtorMetadataMap().forEach((key, debtorMetadata) -> {
            if (key.equals(BIZ_EVENT_ID + 0)) {
                assertNotNull(debtorMetadata.getErrorMessage());
                assertNull(debtorMetadata.getDocumentName());
                assertNull(debtorMetadata.getDocumentUrl());
                assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), debtorMetadata.getStatusCode());
            } else if (key.equals(BIZ_EVENT_ID + 1)) {
                assertNull(debtorMetadata.getErrorMessage());
                assertNotNull(debtorMetadata.getDocumentName());
                assertNotNull(debtorMetadata.getDocumentUrl());
                assertEquals(HttpStatus.SC_OK, debtorMetadata.getStatusCode());
            } else {
                fail();
            }
        });
        assertNull(result.getPayerMetadata());

        verify(buildTemplateServiceMock, times(totalNotice)).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        verify(pdfEngineClientMock, times(1)).generatePDF(any(), any());
        verify(receiptBlobClientMock, times(1)).savePdfToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsPayerNullFailSaveToBlobStorageThrowsException() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse(HttpStatus.SC_OK, outputPdfDebtor.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doThrow(RuntimeException.class)
                .doReturn(getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = getCartForReceipt(
                null,
                DEBTOR_FISCAL_CODE,
                totalNotice,
                false,
                false
        );

        PdfCartGeneration result =
                assertDoesNotThrow(() -> sut.generateCartReceipts(cartForReceipt, bizEventList, WORKING_DIR_PATH));

        assertNotNull(result);
        assertNotNull(result.getDebtorMetadataMap());
        assertEquals(2, result.getDebtorMetadataMap().size());
        result.getDebtorMetadataMap().forEach((key, debtorMetadata) -> {
            if (key.equals(BIZ_EVENT_ID + 0)) {
                assertNotNull(debtorMetadata.getErrorMessage());
                assertNull(debtorMetadata.getDocumentName());
                assertNull(debtorMetadata.getDocumentUrl());
                assertEquals(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), debtorMetadata.getStatusCode());
            } else if (key.equals(BIZ_EVENT_ID + 1)) {
                assertNull(debtorMetadata.getErrorMessage());
                assertNotNull(debtorMetadata.getDocumentName());
                assertNotNull(debtorMetadata.getDocumentUrl());
                assertEquals(HttpStatus.SC_OK, debtorMetadata.getStatusCode());
            } else {
                fail();
            }
        });
        assertNull(result.getPayerMetadata());

        verify(buildTemplateServiceMock, times(totalNotice)).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        verify(pdfEngineClientMock, times(totalNotice)).generatePDF(any(), any());
        verify(receiptBlobClientMock, times(totalNotice)).savePdfToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsPayerNullFailSaveToBlobStorageReturn500() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse(HttpStatus.SC_OK, outputPdfDebtor.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(
                getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.INTERNAL_SERVER_ERROR.value()),
                getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value())
        ).when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = getCartForReceipt(
                null,
                DEBTOR_FISCAL_CODE,
                totalNotice,
                false,
                false
        );

        PdfCartGeneration result =
                assertDoesNotThrow(() -> sut.generateCartReceipts(cartForReceipt, bizEventList, WORKING_DIR_PATH));

        assertNotNull(result);
        assertNotNull(result.getDebtorMetadataMap());
        assertEquals(2, result.getDebtorMetadataMap().size());
        result.getDebtorMetadataMap().forEach((key, debtorMetadata) -> {
            if (key.equals(BIZ_EVENT_ID + 0)) {
                assertNotNull(debtorMetadata.getErrorMessage());
                assertNull(debtorMetadata.getDocumentName());
                assertNull(debtorMetadata.getDocumentUrl());
                assertEquals(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), debtorMetadata.getStatusCode());
            } else if (key.equals(BIZ_EVENT_ID + 1)) {
                assertNull(debtorMetadata.getErrorMessage());
                assertNotNull(debtorMetadata.getDocumentName());
                assertNotNull(debtorMetadata.getDocumentUrl());
                assertEquals(HttpStatus.SC_OK, debtorMetadata.getStatusCode());
            } else {
                fail();
            }
        });
        assertNull(result.getPayerMetadata());

        verify(buildTemplateServiceMock, times(totalNotice)).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        verify(pdfEngineClientMock, times(totalNotice)).generatePDF(any(), any());
        verify(receiptBlobClientMock, times(totalNotice)).savePdfToBlobStorage(any(), any());
    }

    private BlobStorageResponse getBlobStorageResponse(int status) {
        BlobStorageResponse blobStorageResponse = new BlobStorageResponse();
        blobStorageResponse.setStatusCode(status);
        if (status == com.microsoft.azure.functions.HttpStatus.CREATED.value()) {
            blobStorageResponse.setDocumentName("document");
            blobStorageResponse.setDocumentUrl("url");
        }
        return blobStorageResponse;
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

    private CartForReceipt getCartForReceipt(
            String payerFiscalCode,
            String debtorFiscalCode,
            int totalNotice,
            boolean debtorAlreadyCreated,
            boolean payerAlreadyCreated
    ) {
        List<CartPayment> cartPayments = new ArrayList<>();

        for (int i = 0; i < totalNotice; i++) {
            cartPayments.add(CartPayment.builder()
                    .bizEventId(BIZ_EVENT_ID + i)
                    .debtorFiscalCode(debtorFiscalCode)
                    .subject(SUBJECT + i)
                    .amount("10")
                    .mdAttach(buildMetadata(debtorAlreadyCreated))
                    .build());
        }

        return CartForReceipt.builder()
                .eventId(CART_ID)
                .payload(
                        Payload.builder()
                                .payerFiscalCode(payerFiscalCode)
                                .totalAmount("100")
                                .mdAttachPayer(buildMetadata(payerAlreadyCreated))
                                .cart(cartPayments)
                                .build()
                )
                .build();
    }

    private List<BizEvent> getBizEventList(int numOfEvents) throws IOException {
        List<BizEvent> bizEventList = new ArrayList<>();
        for (int i = 0; i < numOfEvents; i++) {
            BizEvent bizEvent = getBizEventFromFile("biz-events/bizEvent_complete_dst_winter.json");
            String bizEvenId = BIZ_EVENT_ID + i;
            bizEvent.setId(bizEvenId);
            bizEventList.add(bizEvent);
        }
        return bizEventList;
    }

    private ReceiptMetadata buildMetadata(boolean build) {
        if (build) {
            return ReceiptMetadata.builder()
                    .name(RECEIPT_METADATA_ORIGINAL_NAME)
                    .url(RECEIPT_METADATA_ORIGINAL_URL)
                    .build();
        }
        return null;
    }
}
