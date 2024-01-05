package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptQueueClient;
import it.gov.pagopa.receipt.pdf.generator.entity.event.*;
import it.gov.pagopa.receipt.pdf.generator.entity.event.Info;
import it.gov.pagopa.receipt.pdf.generator.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.service.impl.ReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GenerateReceiptPdfTest {

    private static final String BIZ_EVENT_VALID_MESSAGE = buildQueueBizEventList(1);
    private static final String MULTIPLE_BIZ_EVENTS_VALID_MESSAGE = buildQueueBizEventList(5);
    private static final String BIZ_EVENT_INVALID_MESSAGE = "invalid message";
    private static final long ORIGINAL_GENERATED_AT = 0L;
    public static final String ID_TRANSACTION = "100";
    public static final String BIZ_EVENT_ID_FIRST = "biz-event-id-1";
    public static final String CF_DEBTOR = "cd debtor";
    public static final String CF_PAYER = "cf payer";

    private GenerateReceiptPdfService generateReceiptPdfServiceMock;
    private ReceiptCosmosClient receiptCosmosClientMock;
    private OutputBinding<Receipt> documentReceiptsMock;
    private ReceiptQueueClient queueServiceMock;
    private ExecutionContext executionContextMock;

    private GenerateReceiptPdf sut;

    @BeforeEach
    void setUp() {
        generateReceiptPdfServiceMock = mock(GenerateReceiptPdfService.class);
        receiptCosmosClientMock = mock(ReceiptCosmosClient.class);
        documentReceiptsMock = (OutputBinding<Receipt>) spy(OutputBinding.class);
        queueServiceMock = mock(ReceiptQueueClient.class);
        executionContextMock = mock(ExecutionContext.class);

        sut = spy(new GenerateReceiptPdf(generateReceiptPdfServiceMock, new ReceiptCosmosServiceImpl(receiptCosmosClientMock), queueServiceMock));
    }

    @Test
    @SneakyThrows
    void generatePDFReceiptWithStatusInsertedSuccess() {
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        assertEquals(ReceiptStatusType.GENERATED, receipt.getStatus());
        assertNotEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());
        assertEquals(BIZ_EVENT_ID_FIRST, receipt.getEventId());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFReceiptWithStatusRetrySuccess() {
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.RETRY, numRetry, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        assertEquals(ReceiptStatusType.GENERATED, receipt.getStatus());
        assertNotEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailParsingBizEventMessage() {
        assertThrows(BizEventNotValidException.class,
                () -> sut.processGenerateReceipt(BIZ_EVENT_INVALID_MESSAGE, documentReceiptsMock, executionContextMock));

        verify(receiptCosmosClientMock, never()).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailReceiptCosmosClientThrowsException() {
        doThrow(ReceiptNotFoundException.class).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        assertThrows(ReceiptNotFoundException.class,
                () -> sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock));

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailReceiptCosmosClientReturnNull() {
        doReturn(null).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        assertThrows(ReceiptNotFoundException.class,
                () -> sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock));

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithNullEventData() {
        doReturn(new Receipt()).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithFailedStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.FAILED, 0, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithGeneratedStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.GENERATED, 0, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithNotQueueSentStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.NOT_QUEUE_SENT, 0, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithIoNotifiedStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.IO_NOTIFIED, 0, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithIOErrorToNotifyStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.IO_ERROR_TO_NOTIFY, 0, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithIONotifierRetryStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.IO_NOTIFIER_RETRY, 0, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithUnableToSendStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.UNABLE_TO_SEND, 0, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithNotToNotifyStatus() {
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.NOT_TO_NOTIFY, 0, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
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

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        assertEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNotNull(receipt.getReasonErr());
        assertNotNull(receipt.getReasonErr().getMessage());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, receipt.getReasonErr().getCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailGoesToRetry() {
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(false).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        Response<SendMessageResult> response = mock(Response.class);
        doReturn(com.microsoft.azure.functions.HttpStatus.CREATED.value()).when(response).getStatusCode();
        doReturn(response).when(queueServiceMock).sendMessageToQueue(any());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        assertEquals(ReceiptStatusType.RETRY, receipt.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry + 1, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock).setValue(any());
        verify(queueServiceMock).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailAndMaxNumRetryReached() {
        int numRetry = 6;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.RETRY, numRetry, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(false).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        assertEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailVerifyThrowsReceiptGenerationNotToRetryException() {
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.RETRY, numRetry, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doThrow(ReceiptGenerationNotToRetryException.class).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        assertEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFReceiptWithMultipleEvents() {
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry, String.valueOf(ID_TRANSACTION));

        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        sut.processGenerateReceipt(MULTIPLE_BIZ_EVENTS_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        assertEquals(ReceiptStatusType.GENERATED, receipt.getStatus());
        assertNotEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());
        assertEquals(String.valueOf(ID_TRANSACTION), receipt.getEventId());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    private Receipt buildReceiptWithStatus(ReceiptStatusType receiptStatusType, int numRetry, String id) {
        return Receipt.builder()
                .eventData(EventData.builder()
                        .debtorFiscalCode(CF_DEBTOR)
                        .payerFiscalCode(CF_PAYER)
                        .build())
                .eventId(id)
                .status(receiptStatusType)
                .numRetry(numRetry)
                .generated_at(ORIGINAL_GENERATED_AT)
                .inserted_at(0L)
                .notified_at(0L)
                .build();
    }

    private static String buildQueueBizEventList(int numberOfEvents) {
        StringBuilder listOfBizEvents = new StringBuilder("[");
        for(int i = 0; i < numberOfEvents; i++){
            try {
                listOfBizEvents.append(ObjectMapperUtils.writeValueAsString(
                        BizEvent.builder()
                                .id("biz-event-id-" + i)
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
                                        .entityUniqueIdentifierValue(CF_DEBTOR)
                                        .build())
                                .payer(Payer.builder()
                                        .fullName("John Doe")
                                        .entityUniqueIdentifierValue(CF_PAYER)
                                        .build())
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
                                                .idTransaction(ID_TRANSACTION)
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
                                .build()
                ));
            } catch (JsonProcessingException ignored) {}

            if(numberOfEvents > 1 && i < numberOfEvents-1){
                listOfBizEvents.append(",");
            }
        }

        listOfBizEvents.append("]");

        return listOfBizEvents.toString();
    }
}
