package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.event.Transaction;
import it.gov.pagopa.receipt.pdf.generator.entity.event.TransactionDetails;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.Aes256Exception;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToSaveException;
import it.gov.pagopa.receipt.pdf.generator.service.impl.ReceiptCosmosServiceImpl;
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
class RetryReviewedPoisonMessagesTest {

    public static final String BIZ_EVENT_ID = "bizEventId";
    public static final String RECEIPT_ERROR_ID = "receiptErrorID";
    public static final String COSMOS_ERROR = "Cosmos Error";
    private static final String ID_TRANSACTION = "100";
    private final String AES_SALT = "salt";
    private final String AES_KEY = "key";

    private String ENCRYPTED_VALID_CONTENT_TO_RETRY;
    private RetryReviewedPoisonMessages function;
    @Mock
    private ExecutionContext context;
    @Mock
    ReceiptQueueClientImpl queueMock;
    @Mock
    ReceiptCosmosServiceImpl cosmosMock;
    @Mock
    Response<SendMessageResult> queueResponse = mock(Response.class);
    @Captor
    private ArgumentCaptor<String> messageCaptor;
    @Captor
    private ArgumentCaptor<List<ReceiptError>> receiptErrorCaptor;
    @Captor
    private ArgumentCaptor<Receipt> receiptCaptor;
    @SuppressWarnings("unchecked")
    OutputBinding<List<ReceiptError>> errorToCosmos = (OutputBinding<List<ReceiptError>>)mock(OutputBinding.class);
    @SystemStub
    private EnvironmentVariables environment = new EnvironmentVariables("AES_SALT", AES_SALT, "AES_SECRET_KEY", AES_KEY);

    @BeforeEach
    public void initiate() throws Aes256Exception {
        ENCRYPTED_VALID_CONTENT_TO_RETRY = Aes256Utils.encrypt(buildQueueBizEventList(1));
    }

    @Test
    void successfulRun() throws JsonProcessingException, ReceiptNotFoundException, UnableToSaveException {
        ReceiptError receiptError = ReceiptError.builder()
                .messagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY)
                .status(ReceiptErrorStatusType.REVIEWED)
                .bizEventId(BIZ_EVENT_ID)
                .id(RECEIPT_ERROR_ID)
                .build();

        Receipt receipt = Receipt.builder().status(ReceiptStatusType.TO_REVIEW).eventId(BIZ_EVENT_ID).build();
        when(cosmosMock.getReceipt(BIZ_EVENT_ID)).thenReturn(receipt);

        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.SC_CREATED);
        when(queueMock.sendMessageToQueue(any())).thenReturn(queueResponse);

        function = spy(new RetryReviewedPoisonMessages(cosmosMock, queueMock));

        assertDoesNotThrow(() -> function.processRetryReviewedPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verify(cosmosMock).updateReceipt(receiptCaptor.capture());
        Receipt receiptCaptorValue = receiptCaptor.getValue();
        assertEquals(BIZ_EVENT_ID, receiptCaptorValue.getEventId());
        assertEquals(ReceiptStatusType.INSERTED, receiptCaptorValue.getStatus());

        verify(queueMock).sendMessageToQueue(messageCaptor.capture());
        BizEvent captured = ObjectMapperUtils.mapBizEventListString(new String(Base64.getMimeDecoder()
                .decode(messageCaptor.getValue())), new TypeReference<>() {}).get(0);
        assertEquals(BIZ_EVENT_ID, captured.getId());

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        ReceiptError receiptErrorCaptorValue = receiptErrorCaptor.getValue().get(0);
        assertEquals(ENCRYPTED_VALID_CONTENT_TO_RETRY, receiptErrorCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.REQUEUED, receiptErrorCaptorValue.getStatus());
        assertEquals(BIZ_EVENT_ID, receiptErrorCaptorValue.getBizEventId());
    }

    @Test
    void successfulRunWithoutElementToRequeue() {
        ReceiptError receiptError = ReceiptError.builder()
                .messagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY)
                .status(ReceiptErrorStatusType.TO_REVIEW)
                .id(RECEIPT_ERROR_ID)
                .build();

        function = spy(new RetryReviewedPoisonMessages(cosmosMock, queueMock));

        assertDoesNotThrow(() -> function.processRetryReviewedPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verifyNoInteractions(queueMock);
        verifyNoInteractions(cosmosMock);
        verifyNoInteractions(errorToCosmos);
    }

    @Test
    void resendToCosmosErrorIfReceiptNotFound() throws ReceiptNotFoundException {
        ReceiptError receiptError = ReceiptError.builder()
                .messagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY)
                .status(ReceiptErrorStatusType.REVIEWED)
                .bizEventId(BIZ_EVENT_ID)
                .id(RECEIPT_ERROR_ID)
                .build();

        doThrow(new ReceiptNotFoundException(COSMOS_ERROR)).when(cosmosMock).getReceipt(BIZ_EVENT_ID);

        function = spy(new RetryReviewedPoisonMessages(cosmosMock, queueMock));

        assertDoesNotThrow(() -> function.processRetryReviewedPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verifyNoInteractions(queueMock);

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        ReceiptError documentCaptorValue = receiptErrorCaptor.getValue().get(0);
        assertEquals(ENCRYPTED_VALID_CONTENT_TO_RETRY, documentCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, documentCaptorValue.getStatus());
        assertEquals(BIZ_EVENT_ID, documentCaptorValue.getBizEventId());
        assertNotNull(documentCaptorValue.getMessageError());
    }

    @Test
    void resendToCosmosErrorIfSaveReceiptFailed() throws ReceiptNotFoundException, UnableToSaveException {
        ReceiptError receiptError = ReceiptError.builder()
                .messagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY)
                .status(ReceiptErrorStatusType.REVIEWED)
                .bizEventId(BIZ_EVENT_ID)
                .id(RECEIPT_ERROR_ID)
                .build();

        Receipt receipt = Receipt.builder().status(ReceiptStatusType.TO_REVIEW).eventId(BIZ_EVENT_ID).build();
        when(cosmosMock.getReceipt(BIZ_EVENT_ID)).thenReturn(receipt);
        doThrow(new UnableToSaveException(COSMOS_ERROR)).when(cosmosMock).updateReceipt(any(Receipt.class));

        function = spy(new RetryReviewedPoisonMessages(cosmosMock, queueMock));

        assertDoesNotThrow(() -> function.processRetryReviewedPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verify(cosmosMock).updateReceipt(receiptCaptor.capture());
        Receipt receiptCaptorValue = receiptCaptor.getValue();
        assertEquals(BIZ_EVENT_ID, receiptCaptorValue.getEventId());
        assertEquals(ReceiptStatusType.INSERTED, receiptCaptorValue.getStatus());

        verifyNoInteractions(queueMock);

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        ReceiptError documentCaptorValue = receiptErrorCaptor.getValue().get(0);
        assertEquals(ENCRYPTED_VALID_CONTENT_TO_RETRY, documentCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, documentCaptorValue.getStatus());
        assertEquals(BIZ_EVENT_ID, documentCaptorValue.getBizEventId());
        assertNotNull(documentCaptorValue.getMessageError());
    }

    @Test
    void resendToCosmosErrorIfQueueFailed() throws JsonProcessingException, ReceiptNotFoundException, UnableToSaveException {
        ReceiptError receiptError = ReceiptError.builder()
                .messagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY)
                .status(ReceiptErrorStatusType.REVIEWED)
                .bizEventId(BIZ_EVENT_ID)
                .id(RECEIPT_ERROR_ID)
                .build();

        Receipt receipt = Receipt.builder().status(ReceiptStatusType.TO_REVIEW).eventId(BIZ_EVENT_ID).build();
        when(cosmosMock.getReceipt(BIZ_EVENT_ID)).thenReturn(receipt);

        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(queueMock.sendMessageToQueue(any())).thenReturn(queueResponse);

        function = spy(new RetryReviewedPoisonMessages(cosmosMock, queueMock));

        assertDoesNotThrow(() -> function.processRetryReviewedPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verify(cosmosMock).updateReceipt(receiptCaptor.capture());
        Receipt receiptCaptorValue = receiptCaptor.getValue();
        assertEquals(BIZ_EVENT_ID, receiptCaptorValue.getEventId());
        assertEquals(ReceiptStatusType.INSERTED, receiptCaptorValue.getStatus());

        verify(queueMock).sendMessageToQueue(messageCaptor.capture());
        BizEvent captured = ObjectMapperUtils.mapBizEventListString(new String(Base64.getMimeDecoder()
                .decode(messageCaptor.getValue())), new TypeReference<>() {}).get(0);
        assertEquals(BIZ_EVENT_ID, captured.getId());

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        ReceiptError documentCaptorValue = receiptErrorCaptor.getValue().get(0);
        assertEquals(ENCRYPTED_VALID_CONTENT_TO_RETRY, documentCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, documentCaptorValue.getStatus());
        assertEquals(BIZ_EVENT_ID, documentCaptorValue.getBizEventId());
        assertNotNull(documentCaptorValue.getMessageError());
    }

    @Test
    void successfulRunMultipleItems() throws JsonProcessingException, ReceiptNotFoundException, UnableToSaveException, Aes256Exception {
        String encryptedMultiItemString = Aes256Utils.encrypt(buildQueueBizEventList(5));
        ReceiptError receiptError = ReceiptError.builder()
                .messagePayload(encryptedMultiItemString)
                .status(ReceiptErrorStatusType.REVIEWED)
                .bizEventId(ID_TRANSACTION)
                .id(RECEIPT_ERROR_ID)
                .build();

        Receipt receipt = Receipt.builder().status(ReceiptStatusType.TO_REVIEW).eventId(ID_TRANSACTION).build();
        when(cosmosMock.getReceipt(ID_TRANSACTION)).thenReturn(receipt);

        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.SC_CREATED);
        when(queueMock.sendMessageToQueue(any())).thenReturn(queueResponse);

        function = spy(new RetryReviewedPoisonMessages(cosmosMock, queueMock));

        assertDoesNotThrow(() -> function.processRetryReviewedPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verify(cosmosMock).updateReceipt(receiptCaptor.capture());
        Receipt receiptCaptorValue = receiptCaptor.getValue();
        assertEquals(ID_TRANSACTION, receiptCaptorValue.getEventId());
        assertEquals(ReceiptStatusType.INSERTED, receiptCaptorValue.getStatus());

        verify(queueMock).sendMessageToQueue(messageCaptor.capture());
        BizEvent captured = ObjectMapperUtils.mapBizEventListString(new String(Base64.getMimeDecoder()
                .decode(messageCaptor.getValue())), new TypeReference<>() {}).get(0);
        assertEquals(BIZ_EVENT_ID, captured.getId());

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        ReceiptError receiptErrorCaptorValue = receiptErrorCaptor.getValue().get(0);
        assertEquals(encryptedMultiItemString, receiptErrorCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.REQUEUED, receiptErrorCaptorValue.getStatus());
        assertEquals(String.valueOf(ID_TRANSACTION), receiptErrorCaptorValue.getBizEventId());
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