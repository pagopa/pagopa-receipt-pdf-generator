package it.gov.pagopa.receipt.pdf.generator.service.impl;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.event.Creditor;
import it.gov.pagopa.receipt.pdf.generator.entity.event.Debtor;
import it.gov.pagopa.receipt.pdf.generator.entity.event.DebtorPosition;
import it.gov.pagopa.receipt.pdf.generator.entity.event.Info;
import it.gov.pagopa.receipt.pdf.generator.entity.event.Payer;
import it.gov.pagopa.receipt.pdf.generator.entity.event.PaymentInfo;
import it.gov.pagopa.receipt.pdf.generator.entity.event.Psp;
import it.gov.pagopa.receipt.pdf.generator.entity.event.Transaction;
import it.gov.pagopa.receipt.pdf.generator.entity.event.TransactionDetails;
import it.gov.pagopa.receipt.pdf.generator.entity.event.TransactionPsp;
import it.gov.pagopa.receipt.pdf.generator.entity.event.WalletItem;
import it.gov.pagopa.receipt.pdf.generator.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.GeneratePDFException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.SavePDFToBlobException;
import it.gov.pagopa.receipt.pdf.generator.exception.TemplateDataMappingException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
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

import java.nio.file.Path;

import static it.gov.pagopa.receipt.pdf.generator.service.impl.GenerateReceiptPdfServiceImpl.ALREADY_CREATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GenerateReceiptPdfServiceImplTest {

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

    @Mock
    private PdfEngineService pdfEngineServiceMock;
    @Mock
    private ReceiptBlobStorageService receiptBlobStorageMock;
    @Mock
    private BuildTemplateService buildTemplateServiceMock;
    @InjectMocks
    private GenerateReceiptPdfServiceImpl sut;

    @Test
    @SneakyThrows
    void generateReceiptsPayerNullWithSuccess() {
        Receipt receiptOnly = getReceiptWithOnlyDebtor(false);
        BizEvent bizEventOnly = getBizEventWithOnlyDebtor();

        doReturn(getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, bizEventOnly, Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(HttpStatus.SC_OK, pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNull(pdfGeneration.getPayerMetadata());

        verify(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));
        verify(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());
    }

    @Test
    @SneakyThrows
    void generateReceiptsSameDebtorPayerWithSuccess() {
        Receipt receiptOnly = getReceiptWithDebtorPayer(VALID_CF_DEBTOR, false, false);
        BizEvent bizEventOnly = getBizEventWithDebtorPayer(VALID_CF_DEBTOR);

        doReturn(getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, bizEventOnly, Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(HttpStatus.SC_OK, pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNull(pdfGeneration.getPayerMetadata());

        verify(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));
        verify(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());
    }

    @Test
    @SneakyThrows
    void generateReceiptsDifferentDebtorPayerWithSuccess() {
        Receipt receiptOnly = getReceiptWithDebtorPayer(VALID_CF_PAYER, false, false);
        BizEvent bizEventOnly = getBizEventWithDebtorPayer(VALID_CF_PAYER);

        doReturn(getPdfEngineResponse(),
                getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(getBlobStorageResponse(), getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, bizEventOnly, Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertFalse(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(HttpStatus.SC_OK, pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNotNull(pdfGeneration.getPayerMetadata());
        assertNull(pdfGeneration.getPayerMetadata().getErrorMessage());
        assertNotNull(pdfGeneration.getPayerMetadata().getDocumentName());
        assertNotNull(pdfGeneration.getPayerMetadata().getDocumentUrl());
        assertEquals(HttpStatus.SC_OK, pdfGeneration.getPayerMetadata().getStatusCode());

        verify(buildTemplateServiceMock, times(2)).buildTemplate(any(), anyBoolean(), any(Receipt.class));
        verify(pdfEngineServiceMock, times(2)).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, times(2)).saveToBlobStorage(any(), anyString());
    }

    @Test
    @SneakyThrows
    void generateReceiptsDifferentDebtorPayerWithSuccessOnDebtAnonym() {
        Receipt receiptOnly = getReceiptWithDebtorPayer(VALID_CF_PAYER, false, false);
        BizEvent listOfBizEvents = getBizEventWithDebtorPayer(VALID_CF_PAYER);

        receiptOnly.getEventData().setDebtorFiscalCode("ANONIMO");

        doReturn(getPdfEngineResponse(),
                getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(getBlobStorageResponse(), getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, listOfBizEvents, Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertFalse(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getPayerMetadata());
        assertNull(pdfGeneration.getPayerMetadata().getErrorMessage());
        assertNotNull(pdfGeneration.getPayerMetadata().getDocumentName());
        assertNotNull(pdfGeneration.getPayerMetadata().getDocumentUrl());
        assertEquals(HttpStatus.SC_OK, pdfGeneration.getPayerMetadata().getStatusCode());

        verify(buildTemplateServiceMock, times(1)).buildTemplate(any(), anyBoolean(), any(Receipt.class));
        verify(pdfEngineServiceMock, times(1)).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, times(1)).saveToBlobStorage(any(), anyString());
    }

    @Test
    @SneakyThrows
    void generateReceiptsPayerNullReceiptAlreadyCreatedWithSuccess() {
        Receipt receiptOnly = getReceiptWithOnlyDebtor(true);
        BizEvent bizEventOnly = getBizEventWithOnlyDebtor();

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, bizEventOnly, Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(ALREADY_CREATED, pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNull(pdfGeneration.getPayerMetadata());

        verify(buildTemplateServiceMock, never()).buildTemplate(any(), anyBoolean(), any(Receipt.class));
        verify(pdfEngineServiceMock, never()).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, never()).saveToBlobStorage(any(), anyString());
    }

    @Test
    @SneakyThrows
    void generateReceiptsSameDebtorPayerAndDebtorReceiptAlreadyCreatedWithSuccess() {
        Receipt receiptOnly = getReceiptWithDebtorPayer(VALID_CF_DEBTOR, true, false);
        BizEvent bizEventOnly = getBizEventWithDebtorPayer(VALID_CF_DEBTOR);

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, bizEventOnly, Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(ALREADY_CREATED, pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNull(pdfGeneration.getPayerMetadata());

        verify(buildTemplateServiceMock, never()).buildTemplate(any(), anyBoolean(), any(Receipt.class));
        verify(pdfEngineServiceMock, never()).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, never()).saveToBlobStorage(any(), anyString());
    }

    @Test
    @SneakyThrows
    void generateReceiptsDifferentDebtorPayerAndPayerReceiptAlreadyCreatedWithSuccess() {
        Receipt receiptOnly = getReceiptWithDebtorPayer(VALID_CF_PAYER, false, true);
        BizEvent bizEventOnly = getBizEventWithDebtorPayer(VALID_CF_PAYER);

        doReturn(getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(getBlobStorageResponse())
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, bizEventOnly, Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertFalse(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(HttpStatus.SC_OK, pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNotNull(pdfGeneration.getPayerMetadata());
        assertNull(pdfGeneration.getPayerMetadata().getErrorMessage());
        assertNull(pdfGeneration.getPayerMetadata().getDocumentName());
        assertNull(pdfGeneration.getPayerMetadata().getDocumentUrl());
        assertEquals(ALREADY_CREATED, pdfGeneration.getPayerMetadata().getStatusCode());

        verify(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));
        verify(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());
    }

    @Test
    @SneakyThrows
    void generateReceiptsPayerNullFailPDFEngineCallReturn500() {
        Receipt receiptOnly = getReceiptWithOnlyDebtor(false);
        BizEvent bizEventOnly = getBizEventWithOnlyDebtor();

        doThrow(new GeneratePDFException(ERROR_MESSAGE, HttpStatus.SC_INTERNAL_SERVER_ERROR))
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, bizEventOnly, Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNotNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNull(pdfGeneration.getPayerMetadata());

        verify(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));
        verify(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, never()).saveToBlobStorage(any(), anyString());
    }

    @Test
    @SneakyThrows
    void generateReceiptsPayerNullFailBuildTemplateData() {
        Receipt receiptOnly = getReceiptWithOnlyDebtor(false);
        BizEvent bizEventOnly = getBizEventWithOnlyDebtor();

        doThrow(new TemplateDataMappingException("error message", ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode()))
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, bizEventOnly, Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNotNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNull(pdfGeneration.getPayerMetadata());

        verify(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));
        verify(pdfEngineServiceMock, never()).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock, never()).saveToBlobStorage(any(), anyString());
    }

    @Test
    @SneakyThrows
    void generateReceiptsPayerNullFailSaveToBlobStorageThrowsException() {
        Receipt receiptOnly = getReceiptWithOnlyDebtor(false);
        BizEvent bizEventOnly = getBizEventWithOnlyDebtor();

        doReturn(getPdfEngineResponse())
                .when(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        doThrow(new SavePDFToBlobException(ERROR_MESSAGE, ReasonErrorCode.ERROR_BLOB_STORAGE.getCode()))
                .when(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, bizEventOnly, Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNotNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNull(pdfGeneration.getPayerMetadata());

        verify(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any(Receipt.class));
        verify(pdfEngineServiceMock).generatePDFReceipt(any(), any());
        verify(receiptBlobStorageMock).saveToBlobStorage(any(), anyString());
    }

    @Test
    @SneakyThrows
    void verifyPayerNullOrSameDebtorPayerWithSuccess() {
        Receipt receipt = buildReceiptForVerify(false, false);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_OK)
                        .documentName(DEBTOR_DOCUMENT_NAME)
                        .documentUrl(DEBTOR_DOCUMENT_URL)
                        .build())
                .generateOnlyDebtor(true)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertTrue(result);
        assertNotNull(receipt.getMdAttach());
        assertNotNull(receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttach().getName());
        assertEquals(DEBTOR_DOCUMENT_NAME, receipt.getMdAttach().getName());
        assertEquals(DEBTOR_DOCUMENT_URL, receipt.getMdAttach().getUrl());
        assertNull(receipt.getMdAttachPayer());
        assertNull(receipt.getReasonErr());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    @SneakyThrows
    void verifyDifferentDebtorPayerWithSuccess() {
        Receipt receipt = buildReceiptForVerify(false, false);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_OK)
                        .documentName(DEBTOR_DOCUMENT_NAME)
                        .documentUrl(DEBTOR_DOCUMENT_URL)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_OK)
                        .documentName(PAYER_DOCUMENT_NAME)
                        .documentUrl(PAYER_DOCUMENT_URL)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertTrue(result);
        assertNotNull(receipt.getMdAttach());
        assertNotNull(receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttach().getName());
        assertEquals(DEBTOR_DOCUMENT_NAME, receipt.getMdAttach().getName());
        assertEquals(DEBTOR_DOCUMENT_URL, receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttachPayer());
        assertNotNull(receipt.getMdAttachPayer().getUrl());
        assertNotNull(receipt.getMdAttachPayer().getName());
        assertEquals(PAYER_DOCUMENT_NAME, receipt.getMdAttachPayer().getName());
        assertEquals(PAYER_DOCUMENT_URL, receipt.getMdAttachPayer().getUrl());
        assertNull(receipt.getReasonErr());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    @SneakyThrows
    void verifyPayerNullOrSameDebtorPayerFailMetadataNull() {
        Receipt receipt = buildReceiptForVerify(false, false);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .generateOnlyDebtor(true)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertFalse(result);
        assertNull(receipt.getMdAttach());
        assertNull(receipt.getMdAttachPayer());
        assertNull(receipt.getReasonErr());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    @SneakyThrows
    void verifyPayerNullOrSameDebtorPayerAlreadyCreatedSuccess() {
        Receipt receipt = buildReceiptForVerify(true, false);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(ALREADY_CREATED)
                        .build())
                .generateOnlyDebtor(true)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertTrue(result);
        assertNotNull(receipt.getMdAttach());
        assertNotNull(receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_NAME, receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_URL, receipt.getMdAttach().getUrl());
        assertNull(receipt.getMdAttachPayer());
        assertNull(receipt.getReasonErr());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    @SneakyThrows
    void verifyPayerNullOrSameDebtorPayerFailReceiptGenerationInError() {
        Receipt receipt = buildReceiptForVerify(false, false);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .generateOnlyDebtor(true)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertFalse(result);
        assertNull(receipt.getMdAttach());
        assertNull(receipt.getMdAttachPayer());
        assertNotNull(receipt.getReasonErr());
        assertNotNull(receipt.getReasonErr().getMessage());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, receipt.getReasonErr().getCode());
        assertEquals(ERROR_MESSAGE, receipt.getReasonErr().getMessage());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    @SneakyThrows
    void verifyPayerNullOrSameDebtorPayerFailThrowsReceiptGenerationNotToRetryException() {
        Receipt receipt = buildReceiptForVerify(false, false);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode())
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .generateOnlyDebtor(true)
                .build();

        assertThrows(ReceiptGenerationNotToRetryException.class, () -> sut.verifyAndUpdateReceipt(receipt, pdfGeneration));

        assertNull(receipt.getMdAttach());
        assertNull(receipt.getMdAttachPayer());
        assertNotNull(receipt.getReasonErr());
        assertNotNull(receipt.getReasonErr().getMessage());
        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), receipt.getReasonErr().getCode());
        assertEquals(ERROR_MESSAGE, receipt.getReasonErr().getMessage());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    @SneakyThrows
    void verifyDifferentDebtorPayerAndDebtorAlreadyGeneratedSuccess() {
        Receipt receipt = buildReceiptForVerify(true, false);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(ALREADY_CREATED)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_OK)
                        .documentName(PAYER_DOCUMENT_NAME)
                        .documentUrl(PAYER_DOCUMENT_URL)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertTrue(result);
        assertNotNull(receipt.getMdAttach());
        assertNotNull(receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_NAME, receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_URL, receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttachPayer());
        assertNotNull(receipt.getMdAttachPayer().getUrl());
        assertNotNull(receipt.getMdAttachPayer().getName());
        assertEquals(PAYER_DOCUMENT_NAME, receipt.getMdAttachPayer().getName());
        assertEquals(PAYER_DOCUMENT_URL, receipt.getMdAttachPayer().getUrl());
        assertNull(receipt.getReasonErr());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    @SneakyThrows
    void verifyDifferentDebtorPayerAndPayerAlreadyGeneratedSuccess() {
        Receipt receipt = buildReceiptForVerify(false, true);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_OK)
                        .documentName(DEBTOR_DOCUMENT_NAME)
                        .documentUrl(DEBTOR_DOCUMENT_URL)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(ALREADY_CREATED)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertTrue(result);
        assertNotNull(receipt.getMdAttach());
        assertNotNull(receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttach().getName());
        assertEquals(DEBTOR_DOCUMENT_NAME, receipt.getMdAttach().getName());
        assertEquals(DEBTOR_DOCUMENT_URL, receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttachPayer());
        assertNotNull(receipt.getMdAttachPayer().getUrl());
        assertNotNull(receipt.getMdAttachPayer().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_NAME, receipt.getMdAttachPayer().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_URL, receipt.getMdAttachPayer().getUrl());
        assertNull(receipt.getReasonErr());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    @SneakyThrows
    void verifyDifferentDebtorPayerFailDebtorGenerationInError() {
        Receipt receipt = buildReceiptForVerify(false, false);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_OK)
                        .documentName(PAYER_DOCUMENT_NAME)
                        .documentUrl(PAYER_DOCUMENT_URL)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertFalse(result);
        assertNull(receipt.getMdAttach());
        assertNotNull(receipt.getReasonErr());
        assertNotNull(receipt.getReasonErr().getMessage());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, receipt.getReasonErr().getCode());
        assertEquals(ERROR_MESSAGE, receipt.getReasonErr().getMessage());
        assertNotNull(receipt.getMdAttachPayer());
        assertNotNull(receipt.getMdAttachPayer().getUrl());
        assertNotNull(receipt.getMdAttachPayer().getName());
        assertEquals(PAYER_DOCUMENT_NAME, receipt.getMdAttachPayer().getName());
        assertEquals(PAYER_DOCUMENT_URL, receipt.getMdAttachPayer().getUrl());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    @SneakyThrows
    void verifyDifferentDebtorPayerFailPayerGenerationInError() {
        Receipt receipt = buildReceiptForVerify(false, false);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_OK)
                        .documentName(DEBTOR_DOCUMENT_NAME)
                        .documentUrl(DEBTOR_DOCUMENT_URL)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertFalse(result);
        assertNotNull(receipt.getMdAttach());
        assertNotNull(receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttach().getName());
        assertEquals(DEBTOR_DOCUMENT_NAME, receipt.getMdAttach().getName());
        assertEquals(DEBTOR_DOCUMENT_URL, receipt.getMdAttach().getUrl());
        assertNull(receipt.getMdAttachPayer());
        assertNull(receipt.getReasonErr());
        assertNotNull(receipt.getReasonErrPayer());
        assertNotNull(receipt.getReasonErrPayer().getMessage());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, receipt.getReasonErrPayer().getCode());
        assertEquals(ERROR_MESSAGE, receipt.getReasonErrPayer().getMessage());
    }

    @Test
    @SneakyThrows
    void verifyDifferentDebtorPayerFailGenerationInErrorForBoth() {
        Receipt receipt = buildReceiptForVerify(false, false);

        String errorMessagePayer = "error message payer";
        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_BAD_REQUEST)
                        .errorMessage(errorMessagePayer)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertFalse(result);
        assertNull(receipt.getMdAttach());
        assertNull(receipt.getMdAttachPayer());
        assertNotNull(receipt.getReasonErr());
        assertNotNull(receipt.getReasonErr().getMessage());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, receipt.getReasonErr().getCode());
        assertEquals(ERROR_MESSAGE, receipt.getReasonErr().getMessage());
        assertNotNull(receipt.getReasonErrPayer());
        assertNotNull(receipt.getReasonErrPayer().getMessage());
        assertEquals(HttpStatus.SC_BAD_REQUEST, receipt.getReasonErrPayer().getCode());
        assertEquals(errorMessagePayer, receipt.getReasonErrPayer().getMessage());
    }

    @Test
    @SneakyThrows
    void verifyDifferentDebtorPayerFailPayerReceiptMetadataNull() {
        Receipt receipt = buildReceiptForVerify(false, false);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_OK)
                        .documentName(DEBTOR_DOCUMENT_NAME)
                        .documentUrl(DEBTOR_DOCUMENT_URL)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertFalse(result);
        assertNotNull(receipt.getMdAttach());
        assertNotNull(receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttach().getName());
        assertEquals(DEBTOR_DOCUMENT_NAME, receipt.getMdAttach().getName());
        assertEquals(DEBTOR_DOCUMENT_URL, receipt.getMdAttach().getUrl());
        assertNull(receipt.getMdAttachPayer());
        assertNull(receipt.getReasonErr());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    @SneakyThrows
    void verifyDifferentDebtorPayerFailThrowsReceiptGenerationNotToRetryException() {
        Receipt receipt = buildReceiptForVerify(false, false);

        String errorMessagePayer = "error message payer";
        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode())
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_BAD_REQUEST)
                        .errorMessage(errorMessagePayer)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        assertThrows(ReceiptGenerationNotToRetryException.class, () -> sut.verifyAndUpdateReceipt(receipt, pdfGeneration));

        assertNull(receipt.getMdAttach());
        assertNull(receipt.getMdAttachPayer());
        assertNotNull(receipt.getReasonErr());
        assertNotNull(receipt.getReasonErr().getMessage());
        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), receipt.getReasonErr().getCode());
        assertEquals(ERROR_MESSAGE, receipt.getReasonErr().getMessage());
        assertNotNull(receipt.getReasonErrPayer());
        assertNotNull(receipt.getReasonErrPayer().getMessage());
        assertEquals(HttpStatus.SC_BAD_REQUEST, receipt.getReasonErrPayer().getCode());
        assertEquals(errorMessagePayer, receipt.getReasonErrPayer().getMessage());
    }

    @Test
    @SneakyThrows
    void verifyDifferentDebtorPayerFailBothThrowsReceiptGenerationNotToRetryException() {
        Receipt receipt = buildReceiptForVerify(false, false);

        String errorMessagePayer = "error message payer";
        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode())
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode())
                        .errorMessage(errorMessagePayer)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        assertThrows(ReceiptGenerationNotToRetryException.class, () -> sut.verifyAndUpdateReceipt(receipt, pdfGeneration));

        assertNull(receipt.getMdAttach());
        assertNull(receipt.getMdAttachPayer());
        assertNotNull(receipt.getReasonErr());
        assertNotNull(receipt.getReasonErr().getMessage());
        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), receipt.getReasonErr().getCode());
        assertEquals(ERROR_MESSAGE, receipt.getReasonErr().getMessage());
        assertNotNull(receipt.getReasonErrPayer());
        assertNotNull(receipt.getReasonErrPayer().getMessage());
        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), receipt.getReasonErrPayer().getCode());
        assertEquals(errorMessagePayer, receipt.getReasonErrPayer().getMessage());
    }

    private Receipt buildReceiptForVerify(boolean debtorAlreadyCreated, boolean payerAlreadyCreated) {
        return Receipt.builder()
                .id("id")
                .mdAttach(buildMetadata(debtorAlreadyCreated))
                .mdAttachPayer(buildMetadata(payerAlreadyCreated))
                .eventData(EventData.builder().debtorFiscalCode("DEBTOR").build())
                .numRetry(0)
                .generated_at(1L)
                .inserted_at(1L)
                .notified_at(1L)
                .build();
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

    private BizEvent getBizEventWithOnlyDebtor() {
        return getBizEventWithDebtorPayer(null);

    }

    private BizEvent getBizEventWithDebtorPayer(String payer) {
        if (payer == null) {
            return getBizEvent(null);
        }
        return getBizEvent(Payer.builder().fullName("John Doe").entityUniqueIdentifierValue(VALID_CF_DEBTOR).build());

    }

    private BizEvent getBizEvent(Payer payer) {
        return BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv("02119891614290410")
                        .modelType("2")
                        .build())
                .creditor(Creditor.builder()
                        .companyName("PA paolo")
                        .officeName("office PA")
                        .build())
                .psp(Psp.builder()
                        .idPsp("60000000001")
                        .psp("PSP Paolo")
                        .build())
                .debtor(Debtor.builder()
                        .fullName("John Doe")
                        .entityUniqueIdentifierValue(VALID_CF_DEBTOR)
                        .build())
                .payer(payer)
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime("2023-04-12T16:21:39.022486")
                        .paymentToken("9a9bad2caf604b86a339476373c659b0")
                        .amount("7000")
                        .fee("200")
                        .remittanceInformation("TARI 2021")
                        .IUR("IUR")
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .wallet(WalletItem.builder().info(Info.builder().brand("MASTER").build()).pagoPa(false).favourite(false).build())
                        .transaction(Transaction.builder()
                                .idTransaction("1")
                                .grandTotal(0L)
                                .amount(7000L)
                                .fee(200L)
                                .rrn("rrn")
                                .authorizationCode("authCode")
                                .creationDate("2023-10-14T00:03:27Z")
                                .psp(TransactionPsp.builder()
                                        .businessName("Nexi")
                                        .serviceName("Nexi")
                                        .build())
                                .build())
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
    }

    private Receipt getReceiptWithOnlyDebtor(boolean alreadyCreated) {
        return getReceiptWithDebtorPayer(null, alreadyCreated, false);
    }

    private Receipt getReceiptWithDebtorPayer(String payer, boolean debtorAlreadyCreated, boolean payerAlreadyCreated) {
        return getReceipt(getEventData(payer), buildMetadata(debtorAlreadyCreated), buildMetadata(payerAlreadyCreated));
    }

    private Receipt getReceipt(EventData eventData, ReceiptMetadata metadataD, ReceiptMetadata metadataP) {
        return Receipt.builder()
                .eventData(eventData)
                .eventId(BIZ_EVENT_ID)
                .mdAttach(metadataD)
                .mdAttachPayer(metadataP)
                .status(ReceiptStatusType.INSERTED)
                .numRetry(0)
                .generated_at(1L)
                .inserted_at(1L)
                .notified_at(1L)
                .build();
    }

    private EventData getEventData(String payer) {
        return EventData.builder()
                .debtorFiscalCode(VALID_CF_DEBTOR)
                .payerFiscalCode(payer)
                .build();
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