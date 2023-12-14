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
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.service.impl.ReceiptCosmosServiceImpl;
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
class ManageReceiptPoisonQueueTest {

    public static final String BIZ_EVENT_ID = "bizEventId";
    private static final long ID_TRANSACTION = 0L;
    private final String AES_SALT = "salt";
    private final String AES_KEY = "key";
    private final String VALID_CONTENT_TO_RETRY = buildQueueBizEventList(1, false);
    private final String VALID_CONTENT_NOT_TO_RETRY = buildQueueBizEventList(1, true);
    private final String VALID_CONTENT_MULTIPLE_ITEMS_NOT_TO_RETRY = buildQueueBizEventList(5, true);
    private final String INVALID_MESSAGE = "invalid message";
    private final Receipt VALID_RECEIPT = Receipt.builder().eventId(BIZ_EVENT_ID).status(ReceiptStatusType.INSERTED).build();
    private final Receipt VALID_MULTIPLE_ITEM_RECEIPT = Receipt.builder().eventId(String.valueOf(ID_TRANSACTION)).status(ReceiptStatusType.INSERTED).build();

    private ManageReceiptPoisonQueue function;

    @Mock
    private ExecutionContext context;

    @Captor
    private ArgumentCaptor<String> messageCaptor;
    @Captor
    private ArgumentCaptor<Receipt> receiptCaptor;
    @Captor
    private ArgumentCaptor<ReceiptError> receiptErrorCaptor;

    @SystemStub
    private EnvironmentVariables environment = new EnvironmentVariables("AES_SALT", AES_SALT, "AES_SECRET_KEY", AES_KEY);

    @Test
    void successRunWithValidPayloadToRetryInQueue() throws Exception {
        ReceiptQueueClientImpl queueMock = mock(ReceiptQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.SC_CREATED);
        when(queueMock.sendMessageToQueue(anyString())).thenReturn(response);

        function = spy(new ManageReceiptPoisonQueue(mock(ReceiptCosmosServiceImpl.class), queueMock));

        OutputBinding<Receipt> receiptOutput = (OutputBinding<Receipt>) mock(OutputBinding.class);
        OutputBinding<ReceiptError> errorToCosmos = (OutputBinding<ReceiptError>) mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageReceiptPoisonQueue(
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
    void successRunWithValidPayloadNotToRetry() throws Aes256Exception, ReceiptNotFoundException {
        ReceiptQueueClientImpl queueMock = mock(ReceiptQueueClientImpl.class);

        ReceiptCosmosService receiptCosmosService = mock(ReceiptCosmosService.class);
        when(receiptCosmosService.getReceipt(BIZ_EVENT_ID)).thenReturn(VALID_RECEIPT);

        function = spy(new ManageReceiptPoisonQueue(receiptCosmosService, queueMock));

        OutputBinding<Receipt> receiptOutput = (OutputBinding<Receipt>) mock(OutputBinding.class);
        OutputBinding<ReceiptError> receiptErrorOutput = (OutputBinding<ReceiptError>) mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageReceiptPoisonQueue(
                VALID_CONTENT_NOT_TO_RETRY, receiptOutput, receiptErrorOutput, context));

        verify(receiptErrorOutput).setValue(receiptErrorCaptor.capture());
        ReceiptError receiptErrorCaptor = this.receiptErrorCaptor.getValue();
        assertNotNull(receiptErrorCaptor.getMessagePayload());
        assertEquals(VALID_CONTENT_NOT_TO_RETRY, Aes256Utils.decrypt(receiptErrorCaptor.getMessagePayload()));
        assertEquals(BIZ_EVENT_ID, receiptErrorCaptor.getBizEventId());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, receiptErrorCaptor.getStatus());

        verify(receiptOutput).setValue(receiptCaptor.capture());
        Receipt receiptCaptor = this.receiptCaptor.getValue();
        assertEquals(BIZ_EVENT_ID, receiptCaptor.getEventId());
        assertEquals(ReceiptStatusType.TO_REVIEW, receiptCaptor.getStatus());
    }

    @Test
    void successRunWithInvalidPayload() throws Aes256Exception {
        function = spy(new ManageReceiptPoisonQueue(mock(ReceiptCosmosService.class), mock(ReceiptQueueClientImpl.class)));

        OutputBinding<Receipt> receiptOutput = (OutputBinding<Receipt>) mock(OutputBinding.class);
        OutputBinding<ReceiptError> errorToCosmos = (OutputBinding<ReceiptError>) mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageReceiptPoisonQueue(INVALID_MESSAGE, receiptOutput, errorToCosmos, context));

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        ReceiptError captured = receiptErrorCaptor.getValue();
        assertNotNull(captured.getMessagePayload());
        assertEquals(INVALID_MESSAGE, Aes256Utils.decrypt(captured.getMessagePayload()));
        assertNull(captured.getBizEventId());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, captured.getStatus());

        verifyNoInteractions(receiptOutput);
    }

    @Test
    void KoRunForRequeueError() throws Aes256Exception, ReceiptNotFoundException {
        ReceiptQueueClientImpl queueMock = mock(ReceiptQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(queueMock.sendMessageToQueue(anyString())).thenReturn(response);

        ReceiptCosmosService receiptCosmosService = mock(ReceiptCosmosService.class);
        when(receiptCosmosService.getReceipt(BIZ_EVENT_ID)).thenReturn(VALID_RECEIPT);

        function = spy(new ManageReceiptPoisonQueue(receiptCosmosService, queueMock));

        OutputBinding<Receipt> receiptOutput = (OutputBinding<Receipt>) mock(OutputBinding.class);
        OutputBinding<ReceiptError> errorToCosmos = (OutputBinding<ReceiptError>) mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageReceiptPoisonQueue(VALID_CONTENT_TO_RETRY, receiptOutput, errorToCosmos, context));

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        ReceiptError captured = receiptErrorCaptor.getValue();
        assertNotNull(captured.getMessagePayload());
        assertEquals(VALID_CONTENT_TO_RETRY, Aes256Utils.decrypt(captured.getMessagePayload()));
        assertEquals(BIZ_EVENT_ID, captured.getBizEventId());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, captured.getStatus());

        verify(receiptOutput).setValue(receiptCaptor.capture());
        Receipt receiptCaptor = this.receiptCaptor.getValue();
        assertEquals(BIZ_EVENT_ID, receiptCaptor.getEventId());
        assertEquals(ReceiptStatusType.TO_REVIEW, receiptCaptor.getStatus());
    }

    @Test
    void successRunWithReceiptNotFound() throws Aes256Exception, ReceiptNotFoundException {
        ReceiptQueueClientImpl queueMock = mock(ReceiptQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(queueMock.sendMessageToQueue(anyString())).thenReturn(response);

        ReceiptCosmosService receiptCosmosService = mock(ReceiptCosmosService.class);
        when(receiptCosmosService.getReceipt(BIZ_EVENT_ID)).thenThrow(ReceiptNotFoundException.class);

        function = spy(new ManageReceiptPoisonQueue(receiptCosmosService, queueMock));

        OutputBinding<Receipt> receiptOutput = (OutputBinding<Receipt>) mock(OutputBinding.class);
        OutputBinding<ReceiptError> errorToCosmos = (OutputBinding<ReceiptError>) mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageReceiptPoisonQueue(VALID_CONTENT_TO_RETRY, receiptOutput, errorToCosmos, context));

        verify(errorToCosmos).setValue(receiptErrorCaptor.capture());
        ReceiptError captured = receiptErrorCaptor.getValue();
        assertNotNull(captured.getMessagePayload());
        assertEquals(VALID_CONTENT_TO_RETRY, Aes256Utils.decrypt(captured.getMessagePayload()));
        assertEquals(BIZ_EVENT_ID, captured.getBizEventId());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, captured.getStatus());

        verifyNoInteractions(receiptOutput);
    }

    @Test
    void successRunWithValidPayloadMultipleItemsNotToRetry() throws Aes256Exception, ReceiptNotFoundException {
        ReceiptQueueClientImpl queueMock = mock(ReceiptQueueClientImpl.class);

        ReceiptCosmosService receiptCosmosService = mock(ReceiptCosmosService.class);
        when(receiptCosmosService.getReceipt(String.valueOf(ID_TRANSACTION))).thenReturn(VALID_MULTIPLE_ITEM_RECEIPT);

        function = spy(new ManageReceiptPoisonQueue(receiptCosmosService, queueMock));

        OutputBinding<Receipt> receiptOutput = (OutputBinding<Receipt>) mock(OutputBinding.class);
        OutputBinding<ReceiptError> receiptErrorOutput = (OutputBinding<ReceiptError>) mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processManageReceiptPoisonQueue(
                VALID_CONTENT_MULTIPLE_ITEMS_NOT_TO_RETRY, receiptOutput, receiptErrorOutput, context));

        verify(receiptErrorOutput).setValue(receiptErrorCaptor.capture());
        ReceiptError receiptErrorCaptor = this.receiptErrorCaptor.getValue();
        assertNotNull(receiptErrorCaptor.getMessagePayload());
        assertEquals(VALID_CONTENT_MULTIPLE_ITEMS_NOT_TO_RETRY, Aes256Utils.decrypt(receiptErrorCaptor.getMessagePayload()));
        assertEquals(String.valueOf(ID_TRANSACTION), receiptErrorCaptor.getBizEventId());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, receiptErrorCaptor.getStatus());

        verify(receiptOutput).setValue(receiptCaptor.capture());
        Receipt receiptCaptor = this.receiptCaptor.getValue();
        assertEquals(String.valueOf(ID_TRANSACTION), receiptCaptor.getEventId());
        assertEquals(ReceiptStatusType.TO_REVIEW, receiptCaptor.getStatus());
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