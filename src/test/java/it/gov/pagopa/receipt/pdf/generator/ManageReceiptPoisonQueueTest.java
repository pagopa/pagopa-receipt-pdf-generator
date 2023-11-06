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
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManageReceiptPoisonQueueTest {

    private final String VALID_CONTENT_TO_RETRY = "{\"id\":\"bizEventId\"}";
    private final String BASE_64_VALID_CONTENT_TO_RETRY = Base64.getMimeEncoder().encodeToString(VALID_CONTENT_TO_RETRY.getBytes());
    private final String VALID_CONTENT_NOT_TO_RETRY = "{\"attemptedPoisonRetry\":\"true\",\"id\":\"bizEventId\"}";
    private final String BASE_64_VALID_CONTENT_NOT_TO_RETRY = Base64.getMimeEncoder().encodeToString(VALID_CONTENT_NOT_TO_RETRY.getBytes());


    private final String INVALID_MESSAGE = "invalid message";
    private final String BASE_64_INVALID_MESSAGE = Base64.getMimeEncoder().encodeToString(INVALID_MESSAGE.getBytes());

    @Spy
    private ManageReceiptPoisonQueue function;

    @Mock
    private ExecutionContext context;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Captor
    private ArgumentCaptor<ReceiptError> documentCaptor;

    @AfterEach
    public void teardown() throws Exception {
        // reset singleton
        Field instance = ReceiptQueueClientImpl.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    void successRunWithValidPayloadToRetryInQueue() throws JsonProcessingException {
        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.SC_CREATED);
        when(serviceMock.sendMessageToQueue(anyString())).thenReturn(response);

        setMock(serviceMock);
        OutputBinding<ReceiptError> errorToCosmos = (OutputBinding<ReceiptError>)mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageReceiptPoisonQueue(
                VALID_CONTENT_TO_RETRY, errorToCosmos, context));

        verify(serviceMock).sendMessageToQueue(messageCaptor.capture());
        BizEvent captured = ObjectMapperUtils.mapString(
                new String(Base64.getMimeDecoder().decode(messageCaptor.getValue())), BizEvent.class);
        assertEquals("bizEventId", captured.getId());
        assertTrue(captured.getAttemptedPoisonRetry());

        verifyNoInteractions(errorToCosmos);

    }

    @Test
    void successRunWithValidPayloadNotToRetry() {
        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);

        ManageReceiptPoisonQueueTest.setMock(serviceMock);
        OutputBinding<ReceiptError> errorToCosmos = (OutputBinding<ReceiptError>)mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageReceiptPoisonQueue(
                VALID_CONTENT_NOT_TO_RETRY, errorToCosmos, context));

        verify(errorToCosmos).setValue(documentCaptor.capture());
        ReceiptError captured = documentCaptor.getValue();
        assertEquals(BASE_64_VALID_CONTENT_NOT_TO_RETRY, captured.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, captured.getStatus());
    }

    @Test
    void successRunWithInvalidPayload() {
        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);

        ManageReceiptPoisonQueueTest.setMock(serviceMock);
        OutputBinding<ReceiptError> errorToCosmos = (OutputBinding<ReceiptError>)mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageReceiptPoisonQueue(INVALID_MESSAGE, errorToCosmos, context));

        verify(errorToCosmos).setValue(documentCaptor.capture());
        ReceiptError captured = documentCaptor.getValue();
        assertEquals(BASE_64_INVALID_MESSAGE, captured.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, captured.getStatus());

    }

    @Test
    void KoRunForRequeueError() {
        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(serviceMock.sendMessageToQueue(anyString())).thenReturn(response);

        setMock(serviceMock);
        OutputBinding<ReceiptError> errorToCosmos = (OutputBinding<ReceiptError>)mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageReceiptPoisonQueue(VALID_CONTENT_TO_RETRY, errorToCosmos, context));

        verify(errorToCosmos).setValue(documentCaptor.capture());
        ReceiptError captured = documentCaptor.getValue();
        assertEquals(BASE_64_VALID_CONTENT_TO_RETRY, captured.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, captured.getStatus());

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