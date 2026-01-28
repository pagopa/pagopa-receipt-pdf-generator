package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.generator.client.impl.CartQueueClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.event.Transaction;
import it.gov.pagopa.receipt.pdf.generator.entity.event.TransactionDetails;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.Aes256Exception;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.service.impl.CartReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.utils.Aes256Utils;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
class ManageCartReceiptPoisonQueueTest {

    public static final String BIZ_EVENT_ID = "bizEventId";
    private static final String ID_TRANSACTION = "100";
    private final String AES_SALT = "salt";
    private final String AES_KEY = "key";
    private final String VALID_CONTENT_TO_RETRY = buildQueueBizEventList(1, false);
    private final String VALID_CONTENT_NOT_TO_RETRY = buildQueueBizEventList(1, true);
    private final String VALID_CONTENT_MULTIPLE_ITEMS_NOT_TO_RETRY = buildQueueBizEventList(5, true);
    private final String INVALID_MESSAGE = "invalid message";
    private final CartForReceipt VALID_RECEIPT = CartForReceipt.builder().cartId(BIZ_EVENT_ID).status(CartStatusType.INSERTED).build();
    private final CartForReceipt VALID_MULTIPLE_ITEM_RECEIPT = CartForReceipt.builder().cartId(String.valueOf(ID_TRANSACTION)).status(CartStatusType.INSERTED).build();

    private ManageCartReceiptPoisonQueue function;

    @Mock
    private ExecutionContext context;

    @Captor
    private ArgumentCaptor<String> messageCaptor;
    @Captor
    private ArgumentCaptor<CartForReceipt> cartReceiptCaptor;
    @Captor
    private ArgumentCaptor<CartReceiptError> cartReceiptErrorCaptor;

    @SystemStub
    private EnvironmentVariables environment = new EnvironmentVariables("AES_SALT", AES_SALT, "AES_SECRET_KEY", AES_KEY);

    @Test
    void successRunWithValidPayloadToRetryInQueue() throws Exception {
        CartQueueClientImpl queueMock = mock(CartQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.SC_CREATED);
        when(queueMock.sendMessageToQueue(anyString())).thenReturn(response);

        function = spy(new ManageCartReceiptPoisonQueue(mock(CartReceiptCosmosServiceImpl.class), queueMock));

        OutputBinding<CartForReceipt> receiptOutput = (OutputBinding<CartForReceipt>) mock(OutputBinding.class);
        OutputBinding<CartReceiptError> errorToCosmos = (OutputBinding<CartReceiptError>) mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageCartReceiptPoisonQueue(
                VALID_CONTENT_TO_RETRY, receiptOutput, errorToCosmos, context));

        verify(queueMock).sendMessageToQueue(messageCaptor.capture());
        BizEvent captured = ObjectMapperUtils.mapBizEventListString(
                new String(Base64.getMimeDecoder().decode(messageCaptor.getValue())), new TypeReference<>() {
                }).get(0);
        assertEquals(BIZ_EVENT_ID, captured.getId());
        assertTrue(captured.getAttemptedPoisonRetry());

        verifyNoInteractions(errorToCosmos);
        verifyNoInteractions(receiptOutput);
    }

    @Test
    void successRunWithValidPayloadNotToRetry() throws Aes256Exception, CartNotFoundException {
        CartQueueClientImpl queueMock = mock(CartQueueClientImpl.class);

        CartReceiptCosmosService receiptCosmosService = mock(CartReceiptCosmosService.class);
        when(receiptCosmosService.getCartForReceipt(ID_TRANSACTION)).thenReturn(VALID_RECEIPT);

        function = spy(new ManageCartReceiptPoisonQueue(receiptCosmosService, queueMock));

        OutputBinding<CartForReceipt> receiptOutput = (OutputBinding<CartForReceipt>) mock(OutputBinding.class);
        OutputBinding<CartReceiptError> receiptErrorOutput = (OutputBinding<CartReceiptError>) mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageCartReceiptPoisonQueue(
                VALID_CONTENT_NOT_TO_RETRY, receiptOutput, receiptErrorOutput, context));

        verify(receiptErrorOutput).setValue(cartReceiptErrorCaptor.capture());
        CartReceiptError receiptErrorCaptor = this.cartReceiptErrorCaptor.getValue();
        assertNotNull(receiptErrorCaptor.getMessagePayload());
        assertEquals(VALID_CONTENT_NOT_TO_RETRY, Aes256Utils.decrypt(receiptErrorCaptor.getMessagePayload()));
        assertEquals(ID_TRANSACTION, receiptErrorCaptor.getId());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, receiptErrorCaptor.getStatus());

        verify(receiptOutput).setValue(cartReceiptCaptor.capture());
        CartForReceipt receiptCaptor = this.cartReceiptCaptor.getValue();
        assertEquals(BIZ_EVENT_ID, receiptCaptor.getCartId());
        assertEquals(CartStatusType.TO_REVIEW, receiptCaptor.getStatus());
    }

    @Test
    void successRunWithInvalidPayload() throws Aes256Exception {
        function = spy(new ManageCartReceiptPoisonQueue(mock(CartReceiptCosmosService.class), mock(CartQueueClientImpl.class)));

        OutputBinding<CartForReceipt> receiptOutput = (OutputBinding<CartForReceipt>) mock(OutputBinding.class);
        OutputBinding<CartReceiptError> errorToCosmos = (OutputBinding<CartReceiptError>) mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageCartReceiptPoisonQueue(INVALID_MESSAGE, receiptOutput, errorToCosmos, context));

        verify(errorToCosmos).setValue(cartReceiptErrorCaptor.capture());
        CartReceiptError captured = cartReceiptErrorCaptor.getValue();
        assertNotNull(captured.getMessagePayload());
        assertEquals(INVALID_MESSAGE, Aes256Utils.decrypt(captured.getMessagePayload()));
        assertNull(captured.getId());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, captured.getStatus());

        verifyNoInteractions(receiptOutput);
    }

    @Test
    void KoRunForRequeueError() throws Aes256Exception, CartNotFoundException {
        CartQueueClientImpl queueMock = mock(CartQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(queueMock.sendMessageToQueue(anyString())).thenReturn(response);

        CartReceiptCosmosService receiptCosmosService = mock(CartReceiptCosmosService.class);
        when(receiptCosmosService.getCartForReceipt(ID_TRANSACTION)).thenReturn(VALID_RECEIPT);

        function = spy(new ManageCartReceiptPoisonQueue(receiptCosmosService, queueMock));

        OutputBinding<CartForReceipt> receiptOutput = (OutputBinding<CartForReceipt>) mock(OutputBinding.class);
        OutputBinding<CartReceiptError> errorToCosmos = (OutputBinding<CartReceiptError>) mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageCartReceiptPoisonQueue(VALID_CONTENT_TO_RETRY, receiptOutput, errorToCosmos, context));

        verify(errorToCosmos).setValue(cartReceiptErrorCaptor.capture());
        CartReceiptError captured = cartReceiptErrorCaptor.getValue();
        assertNotNull(captured.getMessagePayload());
        assertEquals(VALID_CONTENT_TO_RETRY, Aes256Utils.decrypt(captured.getMessagePayload()));
        assertEquals(ID_TRANSACTION, captured.getId());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, captured.getStatus());

        verify(receiptOutput).setValue(cartReceiptCaptor.capture());
        CartForReceipt receiptCaptor = this.cartReceiptCaptor.getValue();
        assertEquals(BIZ_EVENT_ID, receiptCaptor.getCartId());
        assertEquals(CartStatusType.TO_REVIEW, receiptCaptor.getStatus());
    }

    @Test
    void successRunWithReceiptNotFound() throws Aes256Exception, CartNotFoundException {
        CartQueueClientImpl queueMock = mock(CartQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(queueMock.sendMessageToQueue(anyString())).thenReturn(response);

        CartReceiptCosmosService receiptCosmosService = mock(CartReceiptCosmosService.class);
        when(receiptCosmosService.getCartForReceipt(ID_TRANSACTION)).thenThrow(CartNotFoundException.class);

        function = spy(new ManageCartReceiptPoisonQueue(receiptCosmosService, queueMock));

        OutputBinding<CartForReceipt> receiptOutput = (OutputBinding<CartForReceipt>) mock(OutputBinding.class);
        OutputBinding<CartReceiptError> errorToCosmos = (OutputBinding<CartReceiptError>) mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageCartReceiptPoisonQueue(VALID_CONTENT_TO_RETRY, receiptOutput, errorToCosmos, context));

        verify(errorToCosmos).setValue(cartReceiptErrorCaptor.capture());
        CartReceiptError captured = cartReceiptErrorCaptor.getValue();
        assertNotNull(captured.getMessagePayload());
        assertEquals(VALID_CONTENT_TO_RETRY, Aes256Utils.decrypt(captured.getMessagePayload()));
        assertEquals(ID_TRANSACTION, captured.getId());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, captured.getStatus());

        verifyNoInteractions(receiptOutput);
    }

    @Test
    void successRunWithValidPayloadMultipleItemsNotToRetry() throws Aes256Exception, CartNotFoundException {
        CartQueueClientImpl queueMock = mock(CartQueueClientImpl.class);

        CartReceiptCosmosService receiptCosmosService = mock(CartReceiptCosmosService.class);
        when(receiptCosmosService.getCartForReceipt(ID_TRANSACTION)).thenReturn(VALID_MULTIPLE_ITEM_RECEIPT);

        function = spy(new ManageCartReceiptPoisonQueue(receiptCosmosService, queueMock));

        OutputBinding<CartForReceipt> receiptOutput = (OutputBinding<CartForReceipt>) mock(OutputBinding.class);
        OutputBinding<CartReceiptError> receiptErrorOutput = (OutputBinding<CartReceiptError>) mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageCartReceiptPoisonQueue(
                VALID_CONTENT_MULTIPLE_ITEMS_NOT_TO_RETRY, receiptOutput, receiptErrorOutput, context));

        verify(receiptErrorOutput).setValue(cartReceiptErrorCaptor.capture());
        CartReceiptError receiptErrorCaptor = this.cartReceiptErrorCaptor.getValue();
        assertNotNull(receiptErrorCaptor.getMessagePayload());
        assertEquals(VALID_CONTENT_MULTIPLE_ITEMS_NOT_TO_RETRY, Aes256Utils.decrypt(receiptErrorCaptor.getMessagePayload()));
        assertEquals(ID_TRANSACTION, receiptErrorCaptor.getId());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, receiptErrorCaptor.getStatus());

        verify(receiptOutput).setValue(cartReceiptCaptor.capture());
        CartForReceipt receiptCaptor = this.cartReceiptCaptor.getValue();
        assertEquals(ID_TRANSACTION, receiptCaptor.getCartId());
        assertEquals(CartStatusType.TO_REVIEW, receiptCaptor.getStatus());
    }

    private static String buildQueueBizEventList(int numberOfEvents, boolean attemptedRetry) {
        List<BizEvent> listOfBizEvents = new ArrayList<>();
        for (int i = 0; i < numberOfEvents; i++) {
            listOfBizEvents.add(
                    BizEvent.builder()
                            .attemptedPoisonRetry(attemptedRetry)
                            .id(BIZ_EVENT_ID + (i == 0 ? "" : i))
                            .transactionDetails(TransactionDetails.builder()
                                    .transaction(Transaction.builder()
                                            .idTransaction(ID_TRANSACTION)
                                            .transactionId(ID_TRANSACTION)
                                            .build())
                                    .build())
                            .build()
            );
        }
        try {
            return ObjectMapperUtils.writeValueAsString(listOfBizEvents);
        } catch (JsonProcessingException ignored) {
            return "";
        }
    }
}