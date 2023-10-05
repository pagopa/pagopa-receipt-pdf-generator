package it.gov.pagopa.receipt.pdf.generator;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateReceiptPdfService;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariable;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

@ExtendWith(MockitoExtension.class)
class GenerateReceiptPdfTest {

    private static final String BIZ_EVENT_VALID_MESSAGE = "{\"id\":\"062-a330-4210-9c67-465b7d641aVS\",\"version\":\"2\",\"idPaymentManager\":null,\"complete\":\"false\",\"receiptId\":\"9a9bad2caf604b86a339476373c659b0\",\"missingInfo\":[\"idPaymentManager\",\"psp.pspPartitaIVA\",\"paymentInfo.primaryCiIncurredFee\",\"paymentInfo.idBundle\",\"paymentInfo.idCiBundle\",\"paymentInfo.metadata\"],\"debtorPosition\":{\"modelType\":\"2\",\"noticeNumber\":\"302119891614290410\",\"iuv\":\"02119891614290410\"},\"creditor\":{\"idPA\":\"66666666666\",\"idBrokerPA\":\"66666666666\",\"idStation\":\"66666666666_01\",\"companyName\":\"PA paolo\",\"officeName\":\"office PA\"},\"psp\":{\"idPsp\":\"60000000001\",\"idBrokerPsp\":\"60000000001\",\"idChannel\":\"60000000001_01\",\"psp\":\"PSP Paolo\",\"pspPartitaIVA\":null,\"pspFiscalCode\":\"CF60000000006\",\"channelDescription\":\"app\"},\"debtor\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"payer\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"paymentInfo\":{\"paymentDateTime\":\"2023-04-12T16:21:39.022486\",\"applicationDate\":\"2021-10-01\",\"transferDate\":\"2021-10-02\",\"dueDate\":\"2021-07-31\",\"paymentToken\":\"9a9bad2caf604b86a339476373c659b0\",\"amount\":\"7000\",\"fee\":\"200\",\"primaryCiIncurredFee\":null,\"idBundle\":null,\"idCiBundle\":null,\"totalNotice\":\"1\",\"paymentMethod\":\"creditCard\",\"touchpoint\":\"app\",\"remittanceInformation\":\"TARI 2021\",\"description\":\"TARI 2021\",\"metadata\":null},\"transferList\":[{\"idTransfer\":\"1\",\"fiscalCodePA\":\"77777777777\",\"companyName\":\"Pa Salvo\",\"amount\":\"7000\",\"transferCategory\":\"0101101IM\",\"remittanceInformation\":\"TARI Comune EC_TE\",\"metadata\":null,\"mbdattachment\":null,\"iban\":\"IT96R0123454321000000012345\"}],\"transactionDetails\":{\"transaction\":{\"psp\":{\"businessName\":\"Nexi\"}},\"wallet\":{\"info\":{\"brand\":\"MASTER\"}}},\"timestamp\":1686919660002,\"properties\":{},\"eventStatus\":\"DONE\",\"eventRetryEnrichmentCount\":0,\"eventTriggeredBySchedule\":false,\"eventErrorMessage\":null}";
    private static final String BIZ_EVENT_INVALID_MESSAGE = "invalid message";
    private static final long ORIGINAL_GENERATED_AT = 0L;

    private GenerateReceiptPdfService generateReceiptPdfServiceMock;
    private ReceiptCosmosClient receiptCosmosClientMock;
    private OutputBinding<Receipt> documentReceiptsMock;
    private OutputBinding<String> requeueMessageMock;
    private ExecutionContext executionContextMock;

    private GenerateReceiptPdf sut;

    @BeforeEach
    void setUp() {
        generateReceiptPdfServiceMock = mock(GenerateReceiptPdfService.class);
        receiptCosmosClientMock = mock(ReceiptCosmosClient.class);
        documentReceiptsMock = (OutputBinding<Receipt>) spy(OutputBinding.class);
        requeueMessageMock = (OutputBinding<String>) spy(OutputBinding.class);
        executionContextMock = mock(ExecutionContext.class);

        sut = spy(new GenerateReceiptPdf(generateReceiptPdfServiceMock, receiptCosmosClientMock));
    }

    @Test
    @SneakyThrows
    void generatePDFReceiptWithStatusInsertedSuccess() {
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        assertEquals(ReceiptStatusType.GENERATED, receipt.getStatus());
        assertNotEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFReceiptWithStatusRetrySuccess() {
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.RETRY, numRetry);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        assertEquals(ReceiptStatusType.GENERATED, receipt.getStatus());
        assertNotEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailParsingBizEventMessage() {
        assertThrows(BizEventNotValidException.class,
                () -> sut.processGenerateReceipt(BIZ_EVENT_INVALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock));

        verify(receiptCosmosClientMock, never()).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailReceiptCosmosClientThrowsException() {
        doThrow(ReceiptNotFoundException.class).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        assertThrows(ReceiptNotFoundException.class,
                () -> sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock));

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailReceiptCosmosClientReturnNull() {
        doReturn(null).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        assertThrows(ReceiptNotFoundException.class,
                () -> sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock));

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithNullEventData() {
        doReturn(new Receipt()).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithFailedStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.FAILED, 0);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithGeneratedStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.GENERATED, 0);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithNotQueueSentStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.NOT_QUEUE_SENT, 0);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithIoNotifiedStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.IO_NOTIFIED, 0);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithIOErrorToNotifyStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.IO_ERROR_TO_NOTIFY, 0);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithIONotifierRetryStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.IO_NOTIFIER_RETRY, 0);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithUnableToSendStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.UNABLE_TO_SEND, 0);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithNotToNotifyStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.NOT_TO_NOTIFY, 0);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailDebtorFiscalCodeNull() {
        int numRetry = 0;
        Receipt receipt = Receipt.builder()
                .eventData(EventData.builder().build())
                .status(ReceiptStatusType.INSERTED)
                .numRetry(numRetry)
                .generated_at(ORIGINAL_GENERATED_AT)
                .inserted_at(0L)
                .notified_at(0L)
                .build();

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        assertEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNotNull(receipt.getReasonErr());
        assertNotNull(receipt.getReasonErr().getMessage());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, receipt.getReasonErr().getCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailGoesToRetry() {
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any());
        doReturn(false).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        assertEquals(ReceiptStatusType.RETRY, receipt.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry + 1, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock).setValue(any());
        verify(requeueMessageMock).setValue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailAndMaxNumRetryReached() {
        int numRetry = 6;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.RETRY, numRetry);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any());
        doReturn(false).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, requeueMessageMock, executionContextMock);

        assertEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock).setValue(any());
        verify(requeueMessageMock, never()).setValue(any());
    }

    private Receipt buildReceiptWithStatus(ReceiptStatusType receiptStatusType, int numRetry) {
        return Receipt.builder()
                .eventData(EventData.builder()
                        .debtorFiscalCode("cd debtor")
                        .payerFiscalCode("cf payer")
                        .build())
                .eventId("biz-event-id")
                .status(receiptStatusType)
                .numRetry(numRetry)
                .generated_at(ORIGINAL_GENERATED_AT)
                .inserted_at(0L)
                .notified_at(0L)
                .build();
    }
}
