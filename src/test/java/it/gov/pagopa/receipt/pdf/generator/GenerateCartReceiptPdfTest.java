package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.generator.client.CartQueueClient;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartPayment;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.Payload;
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
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.CartReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfCartGeneration;
import it.gov.pagopa.receipt.pdf.generator.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateCartReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GenerateCartReceiptPdfTest {

    private static final long ORIGINAL_GENERATED_AT = 0L;
    private static final String ID_TRANSACTION = "100";
    private static final String CF_DEBTOR = "cd debtor";
    private static final String CF_PAYER = "cf payer";
    private static final String BIZ_EVENT_ID = "062-a330-4210-9c67-465b7d641aVS";

    @Mock
    private GenerateCartReceiptPdfService generateCartReceiptPdfServiceMock;
    @Mock
    private CartReceiptCosmosService cartReceiptCosmosServiceMock;
    @Mock
    private CartQueueClient cartQueueClientMock;

    @Mock
    private OutputBinding<CartForReceipt> cartForReceiptsBindingMock;
    @Mock
    private ExecutionContext executionContextMock;
    @Mock
    private Response<SendMessageResult> queueResponseMock;

    @InjectMocks
    private GenerateCartReceiptPdf sut;

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"INSERTED", "RETRY"})
    @SneakyThrows
    void processGenerateCartReceiptSuccess(CartStatusType statusType) {
        int numRetry = 0;
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceipt(CF_PAYER, CF_DEBTOR, totalNotice, statusType);

        doReturn(cart).when(cartReceiptCosmosServiceMock).getCartForReceipt(ID_TRANSACTION);
        doReturn(new PdfCartGeneration()).when(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), any(), any());
        doReturn(true).when(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());

        assertDoesNotThrow(() ->
                sut.processGenerateCartReceipt(
                        buildQueueBizEventList(totalNotice),
                        cartForReceiptsBindingMock,
                        executionContextMock)
        );

        assertEquals(CartStatusType.GENERATED, cart.getStatus());
        assertNotEquals(ORIGINAL_GENERATED_AT, cart.getGenerated_at());
        assertEquals(numRetry, cart.getNumRetry());
        assertNull(cart.getReasonErr());
        assertEquals(ID_TRANSACTION, cart.getEventId());

        verify(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());
        verify(cartForReceiptsBindingMock).setValue(any());
        verify(cartQueueClientMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void processGenerateCartReceiptFailParsingBizEvents() {
        assertThrows(BizEventNotValidException.class,
                () -> sut.processGenerateCartReceipt(
                        "invalid biz event list",
                        cartForReceiptsBindingMock,
                        executionContextMock)
        );

        verify(cartReceiptCosmosServiceMock, never()).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(cartForReceiptsBindingMock, never()).setValue(any());
        verify(cartQueueClientMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void processGenerateCartReceiptDiscardedNoEvents() {
        assertDoesNotThrow(() ->
                sut.processGenerateCartReceipt(
                        "[]",
                        cartForReceiptsBindingMock,
                        executionContextMock)
        );

        verify(cartReceiptCosmosServiceMock, never()).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(cartForReceiptsBindingMock, never()).setValue(any());
        verify(cartQueueClientMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void processGenerateCartReceiptFailCartNotFound() {
        doThrow(CartNotFoundException.class).when(cartReceiptCosmosServiceMock).getCartForReceipt(ID_TRANSACTION);

        assertThrows(
                CartNotFoundException.class,
                () -> sut.processGenerateCartReceipt(
                        buildQueueBizEventList(2),
                        cartForReceiptsBindingMock,
                        executionContextMock)
        );

        verify(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(cartForReceiptsBindingMock, never()).setValue(any());
        verify(cartQueueClientMock, never()).sendMessageToQueue(any());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"INSERTED", "RETRY"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void processGenerateCartReceiptDiscardedCartNotInValidState(CartStatusType statusType) {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceipt(CF_PAYER, CF_DEBTOR, totalNotice, statusType);

        doReturn(cart).when(cartReceiptCosmosServiceMock).getCartForReceipt(ID_TRANSACTION);

        assertDoesNotThrow(() ->
                sut.processGenerateCartReceipt(
                        buildQueueBizEventList(totalNotice),
                        cartForReceiptsBindingMock,
                        executionContextMock)
        );

        verify(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(cartForReceiptsBindingMock, never()).setValue(any());
        verify(cartQueueClientMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void processGenerateCartReceiptDiscardedCartPayloadNull() {
        int totalNotice = 2;

        doReturn(new CartForReceipt()).when(cartReceiptCosmosServiceMock).getCartForReceipt(ID_TRANSACTION);

        assertDoesNotThrow(() ->
                sut.processGenerateCartReceipt(
                        buildQueueBizEventList(totalNotice),
                        cartForReceiptsBindingMock,
                        executionContextMock)
        );

        verify(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(cartForReceiptsBindingMock, never()).setValue(any());
        verify(cartQueueClientMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void processGenerateCartReceiptDiscardedTotalNoticeMismatch() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceipt(CF_PAYER, CF_DEBTOR, totalNotice, CartStatusType.INSERTED);

        doReturn(cart).when(cartReceiptCosmosServiceMock).getCartForReceipt(ID_TRANSACTION);

        assertDoesNotThrow(() ->
                sut.processGenerateCartReceipt(
                        buildQueueBizEventList(3),
                        cartForReceiptsBindingMock,
                        executionContextMock)
        );

        assertEquals(CartStatusType.FAILED, cart.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, cart.getGenerated_at());
        assertEquals(ID_TRANSACTION, cart.getEventId());
        assertNotNull(cart.getReasonErr());
        assertNotNull(cart.getReasonErr().getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), cart.getReasonErr().getCode());

        verify(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(cartForReceiptsBindingMock).setValue(any());
        verify(cartQueueClientMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void processGenerateCartReceiptDiscardedAllFiscalCodesAreNull() {
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceipt(null, null, totalNotice, CartStatusType.INSERTED);

        doReturn(cart).when(cartReceiptCosmosServiceMock).getCartForReceipt(ID_TRANSACTION);

        assertDoesNotThrow(() ->
                sut.processGenerateCartReceipt(
                        buildQueueBizEventList(totalNotice),
                        cartForReceiptsBindingMock,
                        executionContextMock)
        );

        assertEquals(CartStatusType.FAILED, cart.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, cart.getGenerated_at());
        assertEquals(ID_TRANSACTION, cart.getEventId());
        assertNotNull(cart.getReasonErr());
        assertNotNull(cart.getReasonErr().getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), cart.getReasonErr().getCode());

        verify(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(cartForReceiptsBindingMock).setValue(any());
        verify(cartQueueClientMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void processGenerateCartReceiptFailGoesToRetry() {
        int numRetry = 0;
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceipt(CF_PAYER, CF_DEBTOR, totalNotice, CartStatusType.INSERTED);

        doReturn(cart).when(cartReceiptCosmosServiceMock).getCartForReceipt(ID_TRANSACTION);
        doReturn(new PdfCartGeneration()).when(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), any(), any());
        doReturn(false).when(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());
        doReturn(queueResponseMock).when(cartQueueClientMock).sendMessageToQueue(any());
        doReturn(com.microsoft.azure.functions.HttpStatus.CREATED.value()).when(queueResponseMock).getStatusCode();

        assertDoesNotThrow(() ->
                sut.processGenerateCartReceipt(
                        buildQueueBizEventList(totalNotice),
                        cartForReceiptsBindingMock,
                        executionContextMock)
        );

        assertEquals(CartStatusType.RETRY, cart.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, cart.getGenerated_at());
        assertEquals(numRetry + 1, cart.getNumRetry());
        assertNull(cart.getReasonErr());

        verify(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());
        verify(cartForReceiptsBindingMock).setValue(any());
        verify(cartQueueClientMock).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void processGenerateCartReceiptFailGoesToRetryButFailToSendOnQueue() {
        int numRetry = 0;
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceipt(CF_PAYER, CF_DEBTOR, totalNotice, CartStatusType.INSERTED);

        doReturn(cart).when(cartReceiptCosmosServiceMock).getCartForReceipt(ID_TRANSACTION);
        doReturn(new PdfCartGeneration()).when(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), any(), any());
        doReturn(false).when(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());
        doReturn(queueResponseMock).when(cartQueueClientMock).sendMessageToQueue(any());
        doReturn(HttpStatus.INTERNAL_SERVER_ERROR.value()).when(queueResponseMock).getStatusCode();

        assertDoesNotThrow(() ->
                sut.processGenerateCartReceipt(
                        buildQueueBizEventList(totalNotice),
                        cartForReceiptsBindingMock,
                        executionContextMock)
        );

        assertEquals(CartStatusType.FAILED, cart.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, cart.getGenerated_at());
        assertEquals(numRetry + 1, cart.getNumRetry());
        assertNull(cart.getReasonErr());

        verify(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());
        verify(cartForReceiptsBindingMock).setValue(any());
        verify(cartQueueClientMock).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void processGenerateCartReceiptFailMaxNumRetryReached() {
        int numRetry = 6;
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceipt(CF_PAYER, CF_DEBTOR, totalNotice, CartStatusType.INSERTED);
        cart.setNumRetry(numRetry);

        doReturn(cart).when(cartReceiptCosmosServiceMock).getCartForReceipt(ID_TRANSACTION);
        doReturn(new PdfCartGeneration()).when(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), any(), any());
        doReturn(false).when(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());

        assertDoesNotThrow(() ->
                sut.processGenerateCartReceipt(
                        buildQueueBizEventList(totalNotice),
                        cartForReceiptsBindingMock,
                        executionContextMock)
        );

        assertEquals(CartStatusType.FAILED, cart.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, cart.getGenerated_at());
        assertEquals(numRetry, cart.getNumRetry());
        assertNull(cart.getReasonErr());

        verify(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());
        verify(cartForReceiptsBindingMock).setValue(any());
        verify(cartQueueClientMock, never()).sendMessageToQueue(any());
    }

    @Test
    @SneakyThrows
    void processGenerateCartReceiptFailVerifyThrowsCartReceiptGenerationNotToRetryException() {
        int numRetry = 6;
        int totalNotice = 2;
        CartForReceipt cart = buildCartForReceipt(CF_PAYER, CF_DEBTOR, totalNotice, CartStatusType.INSERTED);
        cart.setNumRetry(numRetry);

        doReturn(cart).when(cartReceiptCosmosServiceMock).getCartForReceipt(ID_TRANSACTION);
        doReturn(new PdfCartGeneration()).when(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), any(), any());
        doThrow(CartReceiptGenerationNotToRetryException.class)
                .when(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());

        assertDoesNotThrow(() ->
                sut.processGenerateCartReceipt(
                        buildQueueBizEventList(totalNotice),
                        cartForReceiptsBindingMock,
                        executionContextMock)
        );

        assertEquals(CartStatusType.FAILED, cart.getStatus());
        assertEquals(ORIGINAL_GENERATED_AT, cart.getGenerated_at());
        assertEquals(numRetry, cart.getNumRetry());
        assertNull(cart.getReasonErr());

        verify(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());
        verify(cartForReceiptsBindingMock).setValue(any());
        verify(cartQueueClientMock, never()).sendMessageToQueue(any());
    }

    private CartForReceipt buildCartForReceipt(
            String payerFiscalCode,
            String debtorFiscalCode,
            int totalNotice,
            CartStatusType statusType
    ) {
        List<CartPayment> cartPayments = new ArrayList<>();

        for (int i = 0; i < totalNotice; i++) {
            cartPayments.add(CartPayment.builder()
                    .bizEventId(BIZ_EVENT_ID + i)
                    .debtorFiscalCode(debtorFiscalCode)
                    .build());
        }

        return CartForReceipt.builder()
                .eventId(ID_TRANSACTION)
                .payload(
                        Payload.builder()
                                .payerFiscalCode(payerFiscalCode)
                                .totalNotice(totalNotice)
                                .cart(cartPayments)
                                .build()
                )
                .generated_at(ORIGINAL_GENERATED_AT)
                .status(statusType)
                .build();
    }

    private static String buildQueueBizEventList(int numberOfEvents) {
        StringBuilder listOfBizEvents = new StringBuilder("[");
        for (int i = 0; i < numberOfEvents; i++) {
            try {
                listOfBizEvents.append(ObjectMapperUtils.writeValueAsString(
                        BizEvent.builder()
                                .id(BIZ_EVENT_ID + i)
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
                                                .transactionId(ID_TRANSACTION)
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