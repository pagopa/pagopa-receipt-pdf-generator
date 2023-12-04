package it.gov.pagopa.receipt.pdf.generator.service.impl;

import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToSaveException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptCosmosServiceImplTest {

    String BIZ_EVENT_ID = "valid biz event id";

    @Mock
    ReceiptCosmosClient receiptCosmosClient;
    @Mock
    CosmosItemResponse<Receipt> saveReceiptResponse;
    ReceiptCosmosServiceImpl function;

    @Test
    void getReceiptSuccess() throws ReceiptNotFoundException {
        when(receiptCosmosClient.getReceiptDocument(BIZ_EVENT_ID)).thenReturn(Receipt.builder().eventId(BIZ_EVENT_ID).build());

        function = spy(new ReceiptCosmosServiceImpl(receiptCosmosClient));

        AtomicReference<Receipt> response = new AtomicReference<>();
        assertDoesNotThrow(() -> response.set(function.getReceipt(BIZ_EVENT_ID)));
        assertEquals(BIZ_EVENT_ID,response.get().getEventId());
    }
    @Test
    void getReceiptFailedWithReceiptNull() throws ReceiptNotFoundException {
        when(receiptCosmosClient.getReceiptDocument(BIZ_EVENT_ID)).thenReturn(null);
        function = spy(new ReceiptCosmosServiceImpl(receiptCosmosClient));
        assertThrows(ReceiptNotFoundException.class, () -> function.getReceipt(BIZ_EVENT_ID));
    }
    @Test
    void getReceiptFailedWithReceiptClientException() throws ReceiptNotFoundException {
        when(receiptCosmosClient.getReceiptDocument(BIZ_EVENT_ID)).thenThrow(ReceiptNotFoundException.class);
        function = spy(new ReceiptCosmosServiceImpl(receiptCosmosClient));
        assertThrows(ReceiptNotFoundException.class, () -> function.getReceipt(BIZ_EVENT_ID));
    }

    @Test
    void saveReceiptSuccess(){
        Receipt receipt = Receipt.builder().eventId(BIZ_EVENT_ID).build();
        when(saveReceiptResponse.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(receiptCosmosClient.saveReceipt(receipt)).thenReturn(saveReceiptResponse);
        function = spy(new ReceiptCosmosServiceImpl(receiptCosmosClient));
        assertDoesNotThrow(() -> function.saveReceipt(receipt));
    }
    @Test
    void saveReceiptFailedNotCreated(){
        Receipt receipt = Receipt.builder().eventId(BIZ_EVENT_ID).build();
        when(saveReceiptResponse.getStatusCode()).thenReturn(HttpStatus.I_AM_A_TEAPOT.value());
        when(receiptCosmosClient.saveReceipt(receipt)).thenReturn(saveReceiptResponse);
        function = spy(new ReceiptCosmosServiceImpl(receiptCosmosClient));
        assertThrows(UnableToSaveException.class,() -> function.saveReceipt(receipt));
    }
    @Test
    void saveReceiptFailedWithReceiptClientException(){
        Receipt receipt = Receipt.builder().eventId(BIZ_EVENT_ID).build();
        when(receiptCosmosClient.saveReceipt(receipt)).thenThrow(CosmosException.class);
        function = spy(new ReceiptCosmosServiceImpl(receiptCosmosClient));
        assertThrows(UnableToSaveException.class,() -> function.saveReceipt(receipt));
    }

}