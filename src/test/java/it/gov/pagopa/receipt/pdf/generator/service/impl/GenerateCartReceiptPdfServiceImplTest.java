package it.gov.pagopa.receipt.pdf.generator.service.impl;

import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartPayment;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.Payload;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.exception.CartReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.GeneratePDFException;
import it.gov.pagopa.receipt.pdf.generator.exception.SavePDFToBlobException;
import it.gov.pagopa.receipt.pdf.generator.exception.TemplateDataMappingException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfCartGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.model.template.ReceiptPDFTemplate;
import it.gov.pagopa.receipt.pdf.generator.service.BuildTemplateService;
import it.gov.pagopa.receipt.pdf.generator.service.PdfEngineService;
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptBlobStorageService;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static it.gov.pagopa.receipt.pdf.generator.service.impl.GenerateReceiptPdfServiceImpl.ALREADY_CREATED;
import static it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtilsTest.getBizEventFromFile;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Mock
    private PdfEngineService pdfEngineServiceMock;
    @Mock
    private ReceiptBlobStorageService receiptBlobStorageMock;
    @Mock
    private BuildTemplateService buildTemplateServiceMock;
    @InjectMocks
    private GenerateCartReceiptPdfServiceImpl sut;

    @Test
    @SneakyThrows
    void generateCartReceiptsPayerNullSuccess() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = buildCartForReceiptWithoutMetadata(
                null,
                DEBTOR_FISCAL_CODE,
                totalNotice
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
        verify(pdfEngineServiceMock, times(totalNotice)).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, times(totalNotice)).saveToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsSameDebtorPayerSuccess() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = buildCartForReceiptWithoutMetadata(
                PAYER_FISCAL_CODE,
                PAYER_FISCAL_CODE,
                totalNotice
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
        verify(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock).saveToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsDifferentDebtorPayerSuccess() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = buildCartForReceiptWithoutMetadata(
                PAYER_FISCAL_CODE,
                DEBTOR_FISCAL_CODE,
                totalNotice
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
        verify(pdfEngineServiceMock, times(totalNotice + 1))
                .generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, times(totalNotice + 1))
                .saveToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsDifferentDebtorPayerAndDebtorAnonimoSuccess() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = buildCartForReceiptWithoutMetadata(
                PAYER_FISCAL_CODE,
                "ANONIMO",
                totalNotice
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
        verify(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock).saveToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsPayerNullAndDebtorReceiptAlreadyCreatedSuccess() {
        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = buildCartForReceipt(
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
        verify(pdfEngineServiceMock, never()).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, never()).saveToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsSameDebtorPayerAndPayerReceiptAlreadyCreatedSuccess() {
        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = buildCartForReceipt(
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
        verify(pdfEngineServiceMock, never()).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, never()).saveToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsDifferentDebtorPayerAndPayerReceiptAlreadyCreatedSuccess() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = buildCartForReceipt(
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
        verify(pdfEngineServiceMock, times(totalNotice)).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, times(totalNotice)).saveToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsPayerNullFailPDFEngineCallReturn500() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doThrow(new GeneratePDFException(ERROR_MESSAGE, HttpStatus.SC_INTERNAL_SERVER_ERROR))
                .doReturn(getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = buildCartForReceiptWithoutMetadata(
                null,
                DEBTOR_FISCAL_CODE,
                totalNotice
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
        verify(pdfEngineServiceMock, times(totalNotice)).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, times(1)).saveToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsPayerNullFailBuildTemplateData() {
        doThrow(new TemplateDataMappingException("error message", ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode()))
                .doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = buildCartForReceiptWithoutMetadata(
                null,
                DEBTOR_FISCAL_CODE,
                totalNotice
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
        verify(pdfEngineServiceMock, times(1)).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, times(1)).saveToBlobStorage(any(), any());
    }

    @Test
    @SneakyThrows
    void generateCartReceiptsPayerNullFailSaveToBlobStorageThrowsException() {
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildCartTemplate(anyList(), anyBoolean(), anyString(), anyString(), anyMap());
        doReturn(getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doThrow(new SavePDFToBlobException(ERROR_MESSAGE, ReasonErrorCode.ERROR_BLOB_STORAGE.getCode()))
                .doReturn(getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());

        int totalNotice = 2;
        List<BizEvent> bizEventList = getBizEventList(totalNotice);
        CartForReceipt cartForReceipt = buildCartForReceiptWithoutMetadata(
                null,
                DEBTOR_FISCAL_CODE,
                totalNotice
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
        verify(pdfEngineServiceMock, times(totalNotice)).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, times(totalNotice)).saveToBlobStorage(any(), any());
    }

    @Test
    void verifySameDebtorPayerSuccess() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceiptWithoutMetadata(PAYER_FISCAL_CODE, PAYER_FISCAL_CODE, totalNotice);
        PdfCartGeneration pdfCartGeneration = PdfCartGeneration.builder()
                .payerMetadata(
                        PdfMetadata.builder()
                                .statusCode(HttpStatus.SC_OK)
                                .documentName(PAYER_DOCUMENT_NAME)
                                .documentUrl(PAYER_DOCUMENT_URL)
                                .build())
                .build();

        Boolean result = assertDoesNotThrow(() -> sut.verifyAndUpdateCartReceipt(cart, pdfCartGeneration));

        assertTrue(result);
        assertNotNull(cart.getPayload());
        assertNotNull(cart.getPayload().getMdAttachPayer());
        assertEquals(PAYER_DOCUMENT_NAME, cart.getPayload().getMdAttachPayer().getName());
        assertEquals(PAYER_DOCUMENT_URL, cart.getPayload().getMdAttachPayer().getUrl());
        assertNull(cart.getPayload().getReasonErrPayer());
        assertNotNull(cart.getPayload().getCart());
        assertEquals(totalNotice, cart.getPayload().getCart().size());
        cart.getPayload().getCart().forEach(cartPayment -> {
            assertNull(cartPayment.getReasonErrDebtor());
            assertNull(cartPayment.getMdAttach());
        });
    }

    @Test
    void verifyPayerNullSuccess() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceiptWithoutMetadata(null, DEBTOR_FISCAL_CODE, totalNotice);
        PdfCartGeneration pdfCartGeneration = PdfCartGeneration.builder()
                .debtorMetadataMap(buildDebtorMetadataMap(totalNotice, HttpStatus.SC_OK))
                .build();

        Boolean result = assertDoesNotThrow(() -> sut.verifyAndUpdateCartReceipt(cart, pdfCartGeneration));

        assertTrue(result);
        assertNotNull(cart.getPayload());
        assertNull(cart.getPayload().getMdAttachPayer());
        assertNull(cart.getPayload().getReasonErrPayer());
        assertNotNull(cart.getPayload().getCart());
        assertEquals(totalNotice, cart.getPayload().getCart().size());
        cart.getPayload().getCart().forEach(cartPayment -> {
            if (cartPayment.getBizEventId().equals(BIZ_EVENT_ID + 0)) {
                assertNotNull(cartPayment.getMdAttach());
                assertEquals(DEBTOR_DOCUMENT_NAME + 0, cartPayment.getMdAttach().getName());
                assertEquals(DEBTOR_DOCUMENT_URL + 0, cartPayment.getMdAttach().getUrl());
                assertNull(cartPayment.getReasonErrDebtor());
            } else if (cartPayment.getBizEventId().equals(BIZ_EVENT_ID + 1)) {
                assertNotNull(cartPayment.getMdAttach());
                assertEquals(DEBTOR_DOCUMENT_NAME + 1, cartPayment.getMdAttach().getName());
                assertEquals(DEBTOR_DOCUMENT_URL + 1, cartPayment.getMdAttach().getUrl());
                assertNull(cartPayment.getReasonErrDebtor());
            } else {
                fail();
            }
        });
    }

    @Test
    void verifyDifferentDebtorPayerSuccess() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceiptWithoutMetadata(PAYER_FISCAL_CODE, DEBTOR_FISCAL_CODE, totalNotice);
        PdfCartGeneration pdfCartGeneration = PdfCartGeneration.builder()
                .debtorMetadataMap(buildDebtorMetadataMap(totalNotice, HttpStatus.SC_OK))
                .payerMetadata(
                        PdfMetadata.builder()
                                .statusCode(HttpStatus.SC_OK)
                                .documentName(PAYER_DOCUMENT_NAME)
                                .documentUrl(PAYER_DOCUMENT_URL)
                                .build())
                .build();

        Boolean result = assertDoesNotThrow(() -> sut.verifyAndUpdateCartReceipt(cart, pdfCartGeneration));

        assertTrue(result);
        assertNotNull(cart.getPayload());
        assertNotNull(cart.getPayload().getMdAttachPayer());
        assertEquals(PAYER_DOCUMENT_NAME, cart.getPayload().getMdAttachPayer().getName());
        assertEquals(PAYER_DOCUMENT_URL, cart.getPayload().getMdAttachPayer().getUrl());
        assertNull(cart.getPayload().getReasonErrPayer());
        assertEquals(totalNotice, cart.getPayload().getCart().size());
        cart.getPayload().getCart().forEach(cartPayment -> {
            if (cartPayment.getBizEventId().equals(BIZ_EVENT_ID + 0)) {
                assertNotNull(cartPayment.getMdAttach());
                assertEquals(DEBTOR_DOCUMENT_NAME + 0, cartPayment.getMdAttach().getName());
                assertEquals(DEBTOR_DOCUMENT_URL + 0, cartPayment.getMdAttach().getUrl());
                assertNull(cartPayment.getReasonErrDebtor());
            } else if (cartPayment.getBizEventId().equals(BIZ_EVENT_ID + 1)) {
                assertNotNull(cartPayment.getMdAttach());
                assertEquals(DEBTOR_DOCUMENT_NAME + 1, cartPayment.getMdAttach().getName());
                assertEquals(DEBTOR_DOCUMENT_URL + 1, cartPayment.getMdAttach().getUrl());
                assertNull(cartPayment.getReasonErrDebtor());
            } else {
                fail();
            }
        });
    }

    @Test
    void verifySameDebtorPayerFailPayerMetadataNull() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceiptWithoutMetadata(PAYER_FISCAL_CODE, PAYER_FISCAL_CODE, totalNotice);
        PdfCartGeneration pdfCartGeneration = PdfCartGeneration.builder()
                .payerMetadata(null)
                .build();

        Boolean result = assertDoesNotThrow(() -> sut.verifyAndUpdateCartReceipt(cart, pdfCartGeneration));

        assertFalse(result);
        assertNotNull(cart.getPayload());
        assertNull(cart.getPayload().getMdAttachPayer());
        assertNull(cart.getPayload().getReasonErrPayer());
        assertEquals(totalNotice, cart.getPayload().getCart().size());
        cart.getPayload().getCart().forEach(cartPayment -> {
            assertNull(cartPayment.getMdAttach());
            assertNull(cartPayment.getReasonErrDebtor());
        });
    }

    @Test
    void verifySameDebtorPayerSuccessPayerAlreadyCreated() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceipt(
                PAYER_FISCAL_CODE,
                PAYER_FISCAL_CODE,
                totalNotice,
                false,
                true
        );
        PdfCartGeneration pdfCartGeneration = PdfCartGeneration.builder()
                .payerMetadata(
                        PdfMetadata.builder()
                                .statusCode(ALREADY_CREATED)
                                .build())
                .build();

        Boolean result = assertDoesNotThrow(() -> sut.verifyAndUpdateCartReceipt(cart, pdfCartGeneration));

        assertTrue(result);
        assertNotNull(cart.getPayload());
        assertNotNull(cart.getPayload().getMdAttachPayer());
        assertEquals(RECEIPT_METADATA_ORIGINAL_NAME, cart.getPayload().getMdAttachPayer().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_URL, cart.getPayload().getMdAttachPayer().getUrl());
        assertNull(cart.getPayload().getReasonErrPayer());
        assertEquals(totalNotice, cart.getPayload().getCart().size());
        cart.getPayload().getCart().forEach(cartPayment -> {
            assertNull(cartPayment.getMdAttach());
            assertNull(cartPayment.getReasonErrDebtor());
        });
    }

    @Test
    void verifySameDebtorPayerFailReceiptGenerationInError() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceiptWithoutMetadata(PAYER_FISCAL_CODE, PAYER_FISCAL_CODE, totalNotice);
        PdfCartGeneration pdfCartGeneration = PdfCartGeneration.builder()
                .payerMetadata(
                        PdfMetadata.builder()
                                .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                                .errorMessage(ERROR_MESSAGE)
                                .build())
                .build();

        Boolean result = assertDoesNotThrow(() -> sut.verifyAndUpdateCartReceipt(cart, pdfCartGeneration));

        assertFalse(result);
        assertNotNull(cart.getPayload());
        assertNull(cart.getPayload().getMdAttachPayer());
        assertNotNull(cart.getPayload().getReasonErrPayer());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, cart.getPayload().getReasonErrPayer().getCode());
        assertEquals(ERROR_MESSAGE, cart.getPayload().getReasonErrPayer().getMessage());
        assertEquals(totalNotice, cart.getPayload().getCart().size());
        cart.getPayload().getCart().forEach(cartPayment -> {
            assertNull(cartPayment.getMdAttach());
            assertNull(cartPayment.getReasonErrDebtor());
        });
    }

    @Test
    void verifySameDebtorPayerFailThrowsReceiptGenerationNotToRetryException() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceiptWithoutMetadata(PAYER_FISCAL_CODE, PAYER_FISCAL_CODE, totalNotice);
        PdfCartGeneration pdfCartGeneration = PdfCartGeneration.builder()
                .payerMetadata(
                        PdfMetadata.builder()
                                .statusCode(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode())
                                .errorMessage(ERROR_MESSAGE)
                                .build())
                .build();

        CartReceiptGenerationNotToRetryException e = assertThrows(
                CartReceiptGenerationNotToRetryException.class,
                () -> sut.verifyAndUpdateCartReceipt(cart, pdfCartGeneration)
        );

        assertNotNull(e);
        assertNotNull(cart.getPayload());
        assertNull(cart.getPayload().getMdAttachPayer());
        assertNotNull(cart.getPayload().getReasonErrPayer());
        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), cart.getPayload().getReasonErrPayer().getCode());
        assertEquals(ERROR_MESSAGE, cart.getPayload().getReasonErrPayer().getMessage());
        assertEquals(totalNotice, cart.getPayload().getCart().size());
        cart.getPayload().getCart().forEach(cartPayment -> {
            assertNull(cartPayment.getMdAttach());
            assertNull(cartPayment.getReasonErrDebtor());
        });
    }

    @Test
    void verifyDifferentDebtorPayerFailDebtorMetadataNull() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceiptWithoutMetadata(
                PAYER_FISCAL_CODE,
                DEBTOR_FISCAL_CODE,
                totalNotice
        );
        Map<String, PdfMetadata> debtorMetadataMap = new HashMap<>();
        debtorMetadataMap.put(BIZ_EVENT_ID + 0, buildDebtorPdfMetadata(HttpStatus.SC_OK, 0));
        debtorMetadataMap.put(BIZ_EVENT_ID + 1, null);
        PdfCartGeneration pdfCartGeneration = PdfCartGeneration.builder()
                .debtorMetadataMap(debtorMetadataMap)
                .payerMetadata(
                        PdfMetadata.builder()
                                .statusCode(HttpStatus.SC_OK)
                                .documentName(PAYER_DOCUMENT_NAME)
                                .documentUrl(PAYER_DOCUMENT_URL)
                                .build())
                .build();

        Boolean result = assertDoesNotThrow(() -> sut.verifyAndUpdateCartReceipt(cart, pdfCartGeneration));

        assertFalse(result);
        assertNotNull(cart.getPayload());
        assertNotNull(cart.getPayload().getMdAttachPayer());
        assertEquals(PAYER_DOCUMENT_NAME, cart.getPayload().getMdAttachPayer().getName());
        assertEquals(PAYER_DOCUMENT_URL, cart.getPayload().getMdAttachPayer().getUrl());
        assertNull(cart.getPayload().getReasonErrPayer());
        assertEquals(totalNotice, cart.getPayload().getCart().size());
        cart.getPayload().getCart().forEach(cartPayment -> {
            if (cartPayment.getBizEventId().equals(BIZ_EVENT_ID + 0)) {
                assertNotNull(cartPayment.getMdAttach());
                assertEquals(DEBTOR_DOCUMENT_NAME + 0, cartPayment.getMdAttach().getName());
                assertEquals(DEBTOR_DOCUMENT_URL + 0, cartPayment.getMdAttach().getUrl());
                assertNull(cartPayment.getReasonErrDebtor());
            } else if (cartPayment.getBizEventId().equals(BIZ_EVENT_ID + 1)) {
                assertNull(cartPayment.getMdAttach());
                assertNull(cartPayment.getReasonErrDebtor());
            } else {
                fail();
            }
        });
    }

    @Test
    void verifyDifferentDebtorPayerSuccessDebtorAlreadyGenerated() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceipt(
                PAYER_FISCAL_CODE,
                DEBTOR_FISCAL_CODE,
                totalNotice,
                true,
                false
        );
        PdfCartGeneration pdfCartGeneration = PdfCartGeneration.builder()
                .debtorMetadataMap(buildDebtorMetadataMap(totalNotice, ALREADY_CREATED))
                .payerMetadata(
                        PdfMetadata.builder()
                                .statusCode(HttpStatus.SC_OK)
                                .documentName(PAYER_DOCUMENT_NAME)
                                .documentUrl(PAYER_DOCUMENT_URL)
                                .build())
                .build();

        Boolean result = assertDoesNotThrow(() -> sut.verifyAndUpdateCartReceipt(cart, pdfCartGeneration));

        assertTrue(result);
        assertNotNull(cart.getPayload());
        assertNotNull(cart.getPayload().getMdAttachPayer());
        assertEquals(PAYER_DOCUMENT_NAME, cart.getPayload().getMdAttachPayer().getName());
        assertEquals(PAYER_DOCUMENT_URL, cart.getPayload().getMdAttachPayer().getUrl());
        assertNull(cart.getPayload().getReasonErrPayer());
        assertEquals(totalNotice, cart.getPayload().getCart().size());
        cart.getPayload().getCart().forEach(cartPayment -> {
            if (cartPayment.getBizEventId().equals(BIZ_EVENT_ID + 0)) {
                assertNotNull(cartPayment.getMdAttach());
                assertEquals(RECEIPT_METADATA_ORIGINAL_NAME + 0, cartPayment.getMdAttach().getName());
                assertEquals(RECEIPT_METADATA_ORIGINAL_URL + 0, cartPayment.getMdAttach().getUrl());
                assertNull(cartPayment.getReasonErrDebtor());
            } else if (cartPayment.getBizEventId().equals(BIZ_EVENT_ID + 1)) {
                assertNotNull(cartPayment.getMdAttach());
                assertEquals(RECEIPT_METADATA_ORIGINAL_NAME + 1, cartPayment.getMdAttach().getName());
                assertEquals(RECEIPT_METADATA_ORIGINAL_URL + 1, cartPayment.getMdAttach().getUrl());
                assertNull(cartPayment.getReasonErrDebtor());
            } else {
                fail();
            }
        });
    }

    @Test
    void verifyDifferentDebtorPayerFailDebtorGenerationError() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceiptWithoutMetadata(
                PAYER_FISCAL_CODE,
                DEBTOR_FISCAL_CODE,
                totalNotice
        );
        Map<String, PdfMetadata> debtorMetadataMap = new HashMap<>();
        debtorMetadataMap.put(BIZ_EVENT_ID + 0, buildDebtorPdfMetadata(HttpStatus.SC_OK, 0));
        debtorMetadataMap.put(BIZ_EVENT_ID + 1, buildDebtorPdfMetadata(HttpStatus.SC_INTERNAL_SERVER_ERROR, 1));
        PdfCartGeneration pdfCartGeneration = PdfCartGeneration.builder()
                .debtorMetadataMap(debtorMetadataMap)
                .payerMetadata(
                        PdfMetadata.builder()
                                .statusCode(HttpStatus.SC_OK)
                                .documentName(PAYER_DOCUMENT_NAME)
                                .documentUrl(PAYER_DOCUMENT_URL)
                                .build())
                .build();

        Boolean result = assertDoesNotThrow(() -> sut.verifyAndUpdateCartReceipt(cart, pdfCartGeneration));

        assertFalse(result);
        assertNotNull(cart.getPayload());
        assertNotNull(cart.getPayload().getMdAttachPayer());
        assertEquals(PAYER_DOCUMENT_NAME, cart.getPayload().getMdAttachPayer().getName());
        assertEquals(PAYER_DOCUMENT_URL, cart.getPayload().getMdAttachPayer().getUrl());
        assertNull(cart.getPayload().getReasonErrPayer());
        assertEquals(totalNotice, cart.getPayload().getCart().size());
        cart.getPayload().getCart().forEach(cartPayment -> {
            if (cartPayment.getBizEventId().equals(BIZ_EVENT_ID + 0)) {
                assertNotNull(cartPayment.getMdAttach());
                assertEquals(DEBTOR_DOCUMENT_NAME + 0, cartPayment.getMdAttach().getName());
                assertEquals(DEBTOR_DOCUMENT_URL + 0, cartPayment.getMdAttach().getUrl());
                assertNull(cartPayment.getReasonErrDebtor());
            } else if (cartPayment.getBizEventId().equals(BIZ_EVENT_ID + 1)) {
                assertNull(cartPayment.getMdAttach());
                assertNotNull(cartPayment.getReasonErrDebtor());
                assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, cartPayment.getReasonErrDebtor().getCode());
                assertEquals(ERROR_MESSAGE + 1, cartPayment.getReasonErrDebtor().getMessage());
            } else {
                fail();
            }
        });
    }

    @Test
    void verifyDifferentDebtorPayerFailThrowsReceiptGenerationNotToRetryException() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceiptWithoutMetadata(
                PAYER_FISCAL_CODE,
                DEBTOR_FISCAL_CODE,
                totalNotice
        );
        Map<String, PdfMetadata> debtorMetadataMap = new HashMap<>();
        debtorMetadataMap.put(BIZ_EVENT_ID + 0, buildDebtorPdfMetadata(HttpStatus.SC_OK, 0));
        debtorMetadataMap.put(BIZ_EVENT_ID + 1, buildDebtorPdfMetadata(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), 1));
        PdfCartGeneration pdfCartGeneration = PdfCartGeneration.builder()
                .debtorMetadataMap(debtorMetadataMap)
                .payerMetadata(
                        PdfMetadata.builder()
                                .statusCode(HttpStatus.SC_OK)
                                .documentName(PAYER_DOCUMENT_NAME)
                                .documentUrl(PAYER_DOCUMENT_URL)
                                .build())
                .build();

        CartReceiptGenerationNotToRetryException e = assertThrows(
                CartReceiptGenerationNotToRetryException.class,
                () -> sut.verifyAndUpdateCartReceipt(cart, pdfCartGeneration)
        );

        assertNotNull(e);
        assertNotNull(cart.getPayload());
        assertNotNull(cart.getPayload().getMdAttachPayer());
        assertEquals(PAYER_DOCUMENT_NAME, cart.getPayload().getMdAttachPayer().getName());
        assertEquals(PAYER_DOCUMENT_URL, cart.getPayload().getMdAttachPayer().getUrl());
        assertNull(cart.getPayload().getReasonErrPayer());
        assertEquals(totalNotice, cart.getPayload().getCart().size());
        cart.getPayload().getCart().forEach(cartPayment -> {
            if (cartPayment.getBizEventId().equals(BIZ_EVENT_ID + 0)) {
                assertNotNull(cartPayment.getMdAttach());
                assertEquals(DEBTOR_DOCUMENT_NAME + 0, cartPayment.getMdAttach().getName());
                assertEquals(DEBTOR_DOCUMENT_URL + 0, cartPayment.getMdAttach().getUrl());
                assertNull(cartPayment.getReasonErrDebtor());
            } else if (cartPayment.getBizEventId().equals(BIZ_EVENT_ID + 1)) {
                assertNull(cartPayment.getMdAttach());
                assertNotNull(cartPayment.getReasonErrDebtor());
                assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), cartPayment.getReasonErrDebtor().getCode());
                assertEquals(ERROR_MESSAGE + 1, cartPayment.getReasonErrDebtor().getMessage());
            } else {
                fail();
            }
        });
    }

    private PdfMetadata getBlobStorageResponse() {
        return PdfMetadata.builder()
                .statusCode(HttpStatus.SC_OK)
                .documentName("document name")
                .documentUrl("document url")
                .build();
    }

    private PdfEngineResponse getPdfEngineResponse() {
        PdfEngineResponse pdfEngineResponse = new PdfEngineResponse();
        pdfEngineResponse.setTempPdfPath("pdfPath");
        pdfEngineResponse.setStatusCode(HttpStatus.SC_OK);
        return pdfEngineResponse;
    }

    private CartForReceipt buildCartForReceiptWithoutMetadata(
            String payerFiscalCode,
            String debtorFiscalCode,
            int totalNotice
    ) {
        return buildCartForReceipt(
                payerFiscalCode,
                debtorFiscalCode,
                totalNotice,
                false,
                false
        );
    }

    private CartForReceipt buildCartForReceipt(
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
                    .mdAttach(buildMetadata(debtorAlreadyCreated, i))
                    .build());
        }

        return CartForReceipt.builder()
                .eventId(CART_ID)
                .payload(
                        Payload.builder()
                                .payerFiscalCode(payerFiscalCode)
                                .totalAmount("100")
                                .mdAttachPayer(buildMetadata(payerAlreadyCreated, null))
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

    private Map<String, PdfMetadata> buildDebtorMetadataMap(int totalNotice, int statusCode) {
        Map<String, PdfMetadata> debtorMetadataMap = new HashMap<>();
        PdfMetadata pdfMetadata;

        for (int i = 0; i < totalNotice; i++) {
            String bizEvenId = BIZ_EVENT_ID + i;
            pdfMetadata = buildDebtorPdfMetadata(statusCode, i);
            debtorMetadataMap.put(
                    bizEvenId,
                    pdfMetadata
            );
        }
        return debtorMetadataMap;
    }

    private PdfMetadata buildDebtorPdfMetadata(int statusCode, int index) {
        PdfMetadata pdfMetadata;
        if (statusCode == ALREADY_CREATED) {
            pdfMetadata = PdfMetadata.builder().statusCode(ALREADY_CREATED).build();
        } else if (statusCode == HttpStatus.SC_OK) {
            pdfMetadata = PdfMetadata.builder()
                    .statusCode(HttpStatus.SC_OK)
                    .documentName(DEBTOR_DOCUMENT_NAME + index)
                    .documentUrl(DEBTOR_DOCUMENT_URL + index)
                    .build();
        } else {
            pdfMetadata = PdfMetadata.builder()
                    .statusCode(statusCode)
                    .errorMessage(ERROR_MESSAGE + index)
                    .build();
        }
        return pdfMetadata;
    }

    private ReceiptMetadata buildMetadata(boolean build, Integer index) {
        if (build) {
            String suffix = index != null ? String.valueOf(index) : "";
            return ReceiptMetadata.builder()
                    .name(RECEIPT_METADATA_ORIGINAL_NAME + suffix)
                    .url(RECEIPT_METADATA_ORIGINAL_URL + suffix)
                    .build();
        }
        return null;
    }
}
