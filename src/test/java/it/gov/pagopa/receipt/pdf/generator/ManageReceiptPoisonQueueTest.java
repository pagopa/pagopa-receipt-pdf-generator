package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.generator.utils.Aes256Utils;
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
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.lang.reflect.Field;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
class ManageReceiptPoisonQueueTest {

    private final String AES_SALT = "salt";
    private final String AES_KEY = "key";
    private final String VALID_CONTENT_TO_RETRY = "{\"id\":\"bizEventId\"}";
    private final String VALID_CONTENT_NOT_TO_RETRY = "{\"attemptedPoisonRetry\":\"true\",\"id\":\"bizEventId\"}";
    private final String INVALID_MESSAGE = "invalid message";

    @Spy
    private ManageReceiptPoisonQueue function;

    @Mock
    private ExecutionContext context;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Captor
    private ArgumentCaptor<ReceiptError> documentCaptor;

    @SystemStub
    private EnvironmentVariables environment = new EnvironmentVariables("AES_SALT", AES_SALT, "AES_SECRET_KEY", AES_KEY);

    @AfterEach
    public void teardown() throws Exception {
        // reset singleton
        Field instance = ReceiptQueueClientImpl.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    void successRunWithValidPayloadToRetryInQueue() throws Exception {
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
        assertNotNull(captured.getMessagePayload());
        assertEquals(VALID_CONTENT_NOT_TO_RETRY, Aes256Utils.decrypt(captured.getMessagePayload(), AES_KEY, AES_SALT));
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
        assertNotNull(captured.getMessagePayload());
        assertEquals(INVALID_MESSAGE,  Aes256Utils.decrypt(captured.getMessagePayload(), AES_KEY, AES_SALT));
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
        assertNotNull(captured.getMessagePayload());
        assertEquals(VALID_CONTENT_TO_RETRY, Aes256Utils.decrypt(captured.getMessagePayload(), AES_KEY, AES_SALT));
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