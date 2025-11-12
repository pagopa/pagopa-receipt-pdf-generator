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
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToSaveException;
import it.gov.pagopa.receipt.pdf.generator.service.impl.CartReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.utils.Aes256Utils;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
class RetryReviewedCartPoisonMessagesTest {

    public static final String BIZ_EVENT_ID = "bizEventId";
    public static final String RECEIPT_ERROR_ID = "receiptErrorID";
    public static final String COSMOS_ERROR = "Cosmos Error";
    private static final String ID_TRANSACTION = "100";
    private final String AES_SALT = "salt";
    private final String AES_KEY = "key";

    private String ENCRYPTED_VALID_CONTENT_TO_RETRY;
    private RetryReviewedCartPoisonMessages function;
    @Mock
    private ExecutionContext context;
    @Mock
    CartQueueClientImpl queueMock;
    @Mock
    CartReceiptCosmosServiceImpl cosmosMock;
    @Mock
    Response<SendMessageResult> queueResponse = mock(Response.class);
    @Captor
    private ArgumentCaptor<String> messageCaptor;
    @Captor
    private ArgumentCaptor<List<CartReceiptError>> receiptErrorCaptor;
    @Captor
    private ArgumentCaptor<CartForReceipt> receiptCaptor;
    @SuppressWarnings("unchecked")
    OutputBinding<List<CartReceiptError>> errorToCosmos = (OutputBinding<List<CartReceiptError>>)mock(OutputBinding.class);
    @SystemStub
    private EnvironmentVariables environment = new EnvironmentVariables("AES_SALT", AES_SALT, "AES_SECRET_KEY", AES_KEY);

    @BeforeEach
    void initiate() throws Aes256Exception {
        ENCRYPTED_VALID_CONTENT_TO_RETRY = Aes256Utils.encrypt(buildQueueBizEventList(1));
    }

    @Test
    void successfulRun() throws JsonProcessingException, UnableToSaveException, CartNotFoundException {
        CartReceiptError receiptError = CartReceiptError.builder()
                .messagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY)
                .status(ReceiptErrorStatusType.REVIEWED)
                .id(ID_TRANSACTION)
                .build();

        CartForReceipt receipt = CartForReceipt.builder().status(CartStatusType.TO_REVIEW).eventId(BIZ_EVENT_ID).build();
        when(cosmosMock.getCartForReceipt(ID_TRANSACTION)).thenReturn(receipt);

        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.SC_CREATED);
        when(queueMock.sendMessageToQueue(any())).thenReturn(queueResponse);

        function = spy(new RetryReviewedCartPoisonMessages(cosmosMock, queueMock));

        assertDoesNotThrow(() -> function.processRetryReviewedCartPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verify(cosmosMock).updateCartForReceipt(receiptCaptor.capture());
        CartForReceipt receiptCaptorValue = receiptCaptor.getValue();
        assertEquals(BIZ_EVENT_ID, receiptCaptorValue.getEventId());
        assertEquals(CartStatusType.INSERTED, receiptCaptorValue.getStatus());

        verify(queueMock).sendMessageToQueue(messageCaptor.capture());
        BizEvent captured = ObjectMapperUtils.mapBizEventListString(new String(Base64.getMimeDecoder()
                .decode(messageCaptor.getValue())), new TypeReference<>() {}).get(0);
        assertEquals(BIZ_EVENT_ID, captured.getId());

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        CartReceiptError receiptErrorCaptorValue = receiptErrorCaptor.getValue().get(0);
        assertEquals(ENCRYPTED_VALID_CONTENT_TO_RETRY, receiptErrorCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.REQUEUED, receiptErrorCaptorValue.getStatus());
        assertEquals(ID_TRANSACTION, receiptErrorCaptorValue.getId());
    }

    @Test
    void successfulRunWithoutElementToRequeue() {
        CartReceiptError receiptError = CartReceiptError.builder()
                .messagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY)
                .status(ReceiptErrorStatusType.TO_REVIEW)
                .id(RECEIPT_ERROR_ID)
                .build();

        function = spy(new RetryReviewedCartPoisonMessages(cosmosMock, queueMock));

        assertDoesNotThrow(() -> function.processRetryReviewedCartPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verifyNoInteractions(queueMock);
        verifyNoInteractions(cosmosMock);
        verifyNoInteractions(errorToCosmos);
    }

    @Test
    void resendToCosmosErrorIfReceiptNotFound() throws CartNotFoundException {
        CartReceiptError receiptError = CartReceiptError.builder()
                .messagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY)
                .status(ReceiptErrorStatusType.REVIEWED)
                .id(ID_TRANSACTION)
                .build();

        doThrow(new CartNotFoundException(COSMOS_ERROR)).when(cosmosMock).getCartForReceipt(ID_TRANSACTION);

        function = spy(new RetryReviewedCartPoisonMessages(cosmosMock, queueMock));

        assertDoesNotThrow(() -> function.processRetryReviewedCartPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verifyNoInteractions(queueMock);

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        CartReceiptError documentCaptorValue = receiptErrorCaptor.getValue().get(0);
        assertEquals(ENCRYPTED_VALID_CONTENT_TO_RETRY, documentCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, documentCaptorValue.getStatus());
        assertEquals(ID_TRANSACTION, documentCaptorValue.getId());
        assertNotNull(documentCaptorValue.getMessageError());
    }

    @Test
    void resendToCosmosErrorIfSaveReceiptFailed() throws UnableToSaveException, CartNotFoundException {
        CartReceiptError receiptError = CartReceiptError.builder()
                .messagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY)
                .status(ReceiptErrorStatusType.REVIEWED)
                .id(ID_TRANSACTION)
                .build();

        CartForReceipt receipt = CartForReceipt.builder().status(CartStatusType.TO_REVIEW).eventId(BIZ_EVENT_ID).build();
        when(cosmosMock.getCartForReceipt(ID_TRANSACTION)).thenReturn(receipt);
        doThrow(new UnableToSaveException(COSMOS_ERROR)).when(cosmosMock).updateCartForReceipt(any(CartForReceipt.class));

        function = spy(new RetryReviewedCartPoisonMessages(cosmosMock, queueMock));

        assertDoesNotThrow(() -> function.processRetryReviewedCartPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verify(cosmosMock).updateCartForReceipt(receiptCaptor.capture());
        CartForReceipt receiptCaptorValue = receiptCaptor.getValue();
        assertEquals(BIZ_EVENT_ID, receiptCaptorValue.getEventId());
        assertEquals(CartStatusType.INSERTED, receiptCaptorValue.getStatus());

        verifyNoInteractions(queueMock);

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        CartReceiptError documentCaptorValue = receiptErrorCaptor.getValue().get(0);
        assertEquals(ENCRYPTED_VALID_CONTENT_TO_RETRY, documentCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, documentCaptorValue.getStatus());
        assertEquals(ID_TRANSACTION, documentCaptorValue.getId());
        assertNotNull(documentCaptorValue.getMessageError());
    }

    @Test
    void resendToCosmosErrorIfQueueFailed() throws JsonProcessingException, UnableToSaveException, CartNotFoundException {
        CartReceiptError receiptError = CartReceiptError.builder()
                .messagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY)
                .status(ReceiptErrorStatusType.REVIEWED)
                .id(ID_TRANSACTION)
                .build();

        CartForReceipt receipt = CartForReceipt.builder().status(CartStatusType.TO_REVIEW).eventId(BIZ_EVENT_ID).build();
        when(cosmosMock.getCartForReceipt(ID_TRANSACTION)).thenReturn(receipt);

        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(queueMock.sendMessageToQueue(any())).thenReturn(queueResponse);

        function = spy(new RetryReviewedCartPoisonMessages(cosmosMock, queueMock));

        assertDoesNotThrow(() -> function.processRetryReviewedCartPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verify(cosmosMock).updateCartForReceipt(receiptCaptor.capture());
        CartForReceipt receiptCaptorValue = receiptCaptor.getValue();
        assertEquals(BIZ_EVENT_ID, receiptCaptorValue.getEventId());
        assertEquals(CartStatusType.INSERTED, receiptCaptorValue.getStatus());

        verify(queueMock).sendMessageToQueue(messageCaptor.capture());
        BizEvent captured = ObjectMapperUtils.mapBizEventListString(new String(Base64.getMimeDecoder()
                .decode(messageCaptor.getValue())), new TypeReference<>() {}).get(0);
        assertEquals(BIZ_EVENT_ID, captured.getId());

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        CartReceiptError documentCaptorValue = receiptErrorCaptor.getValue().get(0);
        assertEquals(ENCRYPTED_VALID_CONTENT_TO_RETRY, documentCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, documentCaptorValue.getStatus());
        assertEquals(ID_TRANSACTION, documentCaptorValue.getId());
        assertNotNull(documentCaptorValue.getMessageError());
    }

    @Test
    void successfulRunMultipleItems() throws JsonProcessingException, UnableToSaveException, Aes256Exception, CartNotFoundException {
        String encryptedMultiItemString = Aes256Utils.encrypt(buildQueueBizEventList(5));
        CartReceiptError receiptError = CartReceiptError.builder()
                .messagePayload(encryptedMultiItemString)
                .status(ReceiptErrorStatusType.REVIEWED)
                .id(ID_TRANSACTION)
                .build();

        CartForReceipt receipt = CartForReceipt.builder().status(CartStatusType.TO_REVIEW).eventId(ID_TRANSACTION).build();
        when(cosmosMock.getCartForReceipt(ID_TRANSACTION)).thenReturn(receipt);

        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.SC_CREATED);
        when(queueMock.sendMessageToQueue(any())).thenReturn(queueResponse);

        function = spy(new RetryReviewedCartPoisonMessages(cosmosMock, queueMock));

        assertDoesNotThrow(() -> function.processRetryReviewedCartPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verify(cosmosMock).updateCartForReceipt(receiptCaptor.capture());
        CartForReceipt receiptCaptorValue = receiptCaptor.getValue();
        assertEquals(ID_TRANSACTION, receiptCaptorValue.getEventId());
        assertEquals(CartStatusType.INSERTED, receiptCaptorValue.getStatus());

        verify(queueMock).sendMessageToQueue(messageCaptor.capture());
        BizEvent captured = ObjectMapperUtils.mapBizEventListString(new String(Base64.getMimeDecoder()
                .decode(messageCaptor.getValue())), new TypeReference<>() {}).get(0);
        assertEquals(BIZ_EVENT_ID, captured.getId());

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        CartReceiptError receiptErrorCaptorValue = receiptErrorCaptor.getValue().get(0);
        assertEquals(encryptedMultiItemString, receiptErrorCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.REQUEUED, receiptErrorCaptorValue.getStatus());
        assertEquals(ID_TRANSACTION, receiptErrorCaptorValue.getId());
    }

    private static String buildQueueBizEventList(int numberOfEvents) {
        List<BizEvent> listOfBizEvents = new ArrayList<>();
        for (int i = 0; i < numberOfEvents; i++) {
            listOfBizEvents.add(
                    BizEvent.builder()
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