package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptQueueClient;
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
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GenerateReceiptPdfTest {

    private static final String BIZ_EVENT_VALID_MESSAGE = buildQueueBizEventList(1);
    private static final String BIZ_EVENT_INVALID_MESSAGE = "invalid message";
    private static final long ORIGINAL_GENERATED_AT = 0L;
    public static final String ID_TRANSACTION = "100";
    public static final String BIZ_EVENT_ID_FIRST = "biz-event-id-1";
    public static final String CF_DEBTOR = "cd debtor";
    public static final String CF_PAYER = "cf payer";

    @Mock
    private GenerateReceiptPdfService generateReceiptPdfServiceMock;
    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;
    @Mock
    private OutputBinding<Receipt> documentReceiptsMock;
    @Mock
    private ReceiptQueueClient queueServiceMock;
    @Mock
    private ExecutionContext executionContextMock;

    @InjectMocks
    private GenerateReceiptPdf sut;

    @ParameterizedTest
    @EnumSource(value = ReceiptStatusType.class, names = {"INSERTED", "RETRY"})
    @SneakyThrows
    void generatePDFReceiptWithStatusInsertedSuccess(ReceiptStatusType statusType) {
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(statusType, numRetry, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosServiceMock).getReceipt(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        assertEquals(ReceiptStatusType.GENERATED, receipt.getStatus());
        assertNotEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());
        assertEquals(BIZ_EVENT_ID_FIRST, receipt.getEventId());

        verify(receiptCosmosServiceMock).getReceipt(anyString());
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

        verify(receiptCosmosServiceMock, never()).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFFailReceiptCosmosClientThrowsException() {
        doThrow(ReceiptNotFoundException.class).when(receiptCosmosServiceMock).getReceipt(anyString());

        assertThrows(ReceiptNotFoundException.class,
                () -> sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock));

        verify(receiptCosmosServiceMock).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void generatePDFDiscardedReceiptWithNullEventData() {
        doReturn(new Receipt()).when(receiptCosmosServiceMock).getReceipt(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        verify(receiptCosmosServiceMock).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentReceiptsMock, never()).setValue(any());
        verify(queueServiceMock, never()).sendMessageToQueue(any());
    }

    @ParameterizedTest
    @EnumSource(value = ReceiptStatusType.class, names = {"INSERTED", "RETRY"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void generatePDFDiscardedReceiptWithFailedStatus(ReceiptStatusType statusType) {
        Receipt receipt = buildReceiptWithStatus(statusType, 0, BIZ_EVENT_ID_FIRST);

        doReturn(receipt).when(receiptCosmosServiceMock).getReceipt(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        verify(receiptCosmosServiceMock).getReceipt(anyString());
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

        doReturn(receipt).when(receiptCosmosServiceMock).getReceipt(anyString());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        assertEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNotNull(receipt.getReasonErr());
        assertNotNull(receipt.getReasonErr().getMessage());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, receipt.getReasonErr().getCode());

        verify(receiptCosmosServiceMock).getReceipt(anyString());
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

        doReturn(receipt).when(receiptCosmosServiceMock).getReceipt(anyString());
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

        verify(receiptCosmosServiceMock).getReceipt(anyString());
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

        doReturn(receipt).when(receiptCosmosServiceMock).getReceipt(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(false).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        assertEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());

        verify(receiptCosmosServiceMock).getReceipt(anyString());
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

        doReturn(receipt).when(receiptCosmosServiceMock).getReceipt(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doThrow(ReceiptGenerationNotToRetryException.class).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        sut.processGenerateReceipt(BIZ_EVENT_VALID_MESSAGE, documentReceiptsMock, executionContextMock);

        assertEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, receipt.getGenerated_at());
        assertEquals(numRetry, receipt.getNumRetry());
        assertNull(receipt.getReasonErr());

        verify(receiptCosmosServiceMock).getReceipt(anyString());
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
        for (int i = 0; i < numberOfEvents; i++) {
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
            } catch (JsonProcessingException ignored) {
                // ignored
            }

            if (numberOfEvents > 1 && i < numberOfEvents - 1) {
                listOfBizEvents.append(",");
            }
        }

        listOfBizEvents.append("]");

        return listOfBizEvents.toString();
    }
}
