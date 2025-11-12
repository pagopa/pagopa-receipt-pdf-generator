package it.gov.pagopa.receipt.pdf.generator.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

@ExtendWith(MockitoExtension.class)
class CartQueueClientImplTest {

    private static final String MESSAGE_TEXT = "a valid message text";

    @Mock
    private QueueClient queueClientMock;

    @Mock
    private Response<SendMessageResult> response;

    @InjectMocks
    private CartQueueClientImpl sut;

    @Test
    void testSingletonConnectionError() throws Exception {
        @SuppressWarnings("secrets:S6338")
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "RECEIPTS_STORAGE_CONN_STRING", "DefaultEndpointsProtocol=https;AccountName=samplequeue;AccountKey=" + mockKey + ";EndpointSuffix=core.windows.net",
                "CART_RECEIPT_QUEUE_TOPIC", "validTopic"
        ).execute(() -> Assertions.assertDoesNotThrow(CartQueueClientImpl::getInstance));
    }

    @Test
    void runOk() {
        when(response.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(queueClientMock.sendMessageWithResponse(eq(MESSAGE_TEXT), any(), eq(null), eq(null), eq(null)))
                .thenReturn(response);

        Response<SendMessageResult> clientResponse = sut.sendMessageToQueue(MESSAGE_TEXT);

        Assertions.assertEquals(HttpStatus.CREATED.value(), clientResponse.getStatusCode());
    }

    @Test
    void runKo() {
        when(response.getStatusCode()).thenReturn(HttpStatus.NO_CONTENT.value());
        when(queueClientMock.sendMessageWithResponse(eq(MESSAGE_TEXT), any(), eq(null), eq(null), eq(null)))
                .thenReturn(response);

        Response<SendMessageResult> clientResponse = sut.sendMessageToQueue(MESSAGE_TEXT);

        Assertions.assertEquals(HttpStatus.NO_CONTENT.value(), clientResponse.getStatusCode());
    }
}