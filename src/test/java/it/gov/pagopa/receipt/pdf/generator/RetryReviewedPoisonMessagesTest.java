package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.Aes256Exception;
import it.gov.pagopa.receipt.pdf.generator.utils.Aes256Utils;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
class RetryReviewedPoisonMessagesTest {

    public static final String BIZ_EVENT_ID = "bizEventId";
    private final String AES_SALT = "salt";
    private final String AES_KEY = "key";

    private String ENCRYPTED_VALID_CONTENT_TO_RETRY;

    @Spy
    private RetryReviewedPoisonMessages function;

    @Mock
    private ExecutionContext context;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Captor
    private ArgumentCaptor<List<ReceiptError>> documentCaptor;
    @SystemStub
    private EnvironmentVariables environment = new EnvironmentVariables("AES_SALT", AES_SALT, "AES_SECRET_KEY", AES_KEY);

    @BeforeEach
    public void initiate() throws Aes256Exception {
        ENCRYPTED_VALID_CONTENT_TO_RETRY = Aes256Utils.encrypt(String.format("{\"id\":\"%s\"}", BIZ_EVENT_ID));
    }

    @AfterEach
    public void teardown() throws Exception {
        // reset singleton
        Field instance = ReceiptQueueClientImpl.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    void successfulRun() throws JsonProcessingException {
        ReceiptError receiptError = new ReceiptError();
        receiptError.setMessagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY);
        receiptError.setStatus(ReceiptErrorStatusType.REVIEWED);
        receiptError.setBizEventId(BIZ_EVENT_ID);
        receiptError.setId("1");

        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.SC_CREATED);
        when(serviceMock.sendMessageToQueue(any())).thenReturn(response);

        setMock(serviceMock);
        OutputBinding<List<ReceiptError>> errorToCosmos = (OutputBinding<List<ReceiptError>>)mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processRetryReviewedPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verify(serviceMock).sendMessageToQueue(messageCaptor.capture());
        BizEvent captured = ObjectMapperUtils.mapString(new String(Base64.getMimeDecoder()
                .decode(messageCaptor.getValue())), BizEvent.class);
        assertEquals(BIZ_EVENT_ID, captured.getId());

        verify(errorToCosmos).setValue(documentCaptor.capture());
        ReceiptError documentCaptorValue = documentCaptor.getValue().get(0);
        assertEquals(ENCRYPTED_VALID_CONTENT_TO_RETRY, documentCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.REQUEUED, documentCaptorValue.getStatus());
        assertEquals(BIZ_EVENT_ID, documentCaptorValue.getBizEventId());

    }

    @Test
    void successfulRunWithoutElementToRequeue() {
        ReceiptError receiptError = new ReceiptError();
        receiptError.setMessagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY);
        receiptError.setStatus(ReceiptErrorStatusType.TO_REVIEW);
        receiptError.setId("1");

        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);
        setMock(serviceMock);
        OutputBinding<List<ReceiptError>> errorToCosmos = (OutputBinding<List<ReceiptError>>)mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processRetryReviewedPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verifyNoInteractions(serviceMock);

    }

    @Test
    void resendToCosmosIfQueueFailed() throws JsonProcessingException {
        ReceiptError receiptError = new ReceiptError();
        receiptError.setMessagePayload(ENCRYPTED_VALID_CONTENT_TO_RETRY);
        receiptError.setStatus(ReceiptErrorStatusType.REVIEWED);
        receiptError.setBizEventId(BIZ_EVENT_ID);
        receiptError.setId("1");

        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(serviceMock.sendMessageToQueue(any())).thenReturn(response);

        setMock(serviceMock);
        OutputBinding<List<ReceiptError>> errorToCosmos = (OutputBinding<List<ReceiptError>>)mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processRetryReviewedPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verify(serviceMock).sendMessageToQueue(messageCaptor.capture());
        BizEvent captured = ObjectMapperUtils.mapString(new String(Base64.getMimeDecoder().decode(
                messageCaptor.getValue())), BizEvent.class);
        assertEquals(BIZ_EVENT_ID, captured.getId());

        verify(errorToCosmos).setValue(documentCaptor.capture());
        ReceiptError documentCaptorValue = documentCaptor.getValue().get(0);
        assertEquals(ENCRYPTED_VALID_CONTENT_TO_RETRY, documentCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, documentCaptorValue.getStatus());
        assertEquals(BIZ_EVENT_ID, documentCaptorValue.getBizEventId());
        assertNotNull(documentCaptorValue.getMessageError());

    }

    static void setMock(ReceiptQueueClientImpl mock) {
        try {
            Field instance = ReceiptQueueClientImpl.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(instance, mock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}