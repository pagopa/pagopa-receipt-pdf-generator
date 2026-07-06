package it.gov.pagopa.receipt.pdf.generator.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

@ExtendWith(MockitoExtension.class)
class ReceiptCosmosClientImplTest {

    public static final String RECEIPT_ID = "a valid receipt id";

    @Mock
    private CosmosClient cosmosClientMock;
    @Mock
    private CosmosDatabase mockDatabase;
    @Mock
    private CosmosContainer mockContainer;
    @Mock
    private CosmosPagedIterable<Receipt> mockIterable;
    @Mock
    private Iterator<Receipt> mockIterator;
    @Mock
    private CosmosItemResponse<Receipt> mockItemResponse;
    @Mock
    private CosmosException mockCosmosException;

    @InjectMocks
    private ReceiptCosmosClientImpl sut;

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_RECEIPT_KEY", mockKey,
                "COSMOS_RECEIPT_SERVICE_ENDPOINT", "",
                "COSMOS_RECEIPT_READ_REGION", ""
        ).execute(() -> Assertions.assertThrows(ExceptionInInitializerError.class, ReceiptCosmosClientImpl::getInstance)
        );
    }

    /**
     * readItem restituisce non-null → viene restituito itemResponse.getItem() direttamente
     */
    @Test
    void getReceiptDocument_readItemSuccess_returnsItem() {
        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        doReturn(mockItemResponse).when(mockContainer).readItem(anyString(), any(PartitionKey.class), any());
        when(mockItemResponse.getItem()).thenReturn(receipt);

        Receipt result = Assertions.assertDoesNotThrow(() -> sut.getReceiptDocument(RECEIPT_ID));

        assertNotNull(result);
        assertEquals(RECEIPT_ID, result.getId());
    }

    /**
     * readItem restituisce null → fallback su queryItems → documento trovato
     */
    @Test
    void getReceiptDocument_readItemNullFallbackToQuery_returnsItem() {
        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        // readItem restituisce null (default mock)
        doReturn(mockIterable).when(mockContainer).queryItems(anyString(), any(), any());
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(receipt);

        Receipt result = Assertions.assertDoesNotThrow(() -> sut.getReceiptDocument(RECEIPT_ID));

        assertEquals(RECEIPT_ID, result.getId());
    }

    /**
     * readItem restituisce null → fallback su queryItems → documento non trovato → ReceiptNotFoundException
     */
    @Test
    void getReceiptDocument_readItemNullFallbackToQuery_notFound_throwsReceiptNotFoundException() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        doReturn(mockIterable).when(mockContainer).queryItems(anyString(), any(), any());
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockIterator.hasNext()).thenReturn(false);

        assertThrows(ReceiptNotFoundException.class, () -> sut.getReceiptDocument("an invalid receipt id"));
    }

    /**
     * readItem lancia CosmosException 404 → fallback su queryItems → documento trovato
     */
    @Test
    void getReceiptDocument_readItem404_fallbackToQuery_returnsItem() {
        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockCosmosException.getStatusCode()).thenReturn(404);
        doThrow(mockCosmosException).when(mockContainer).readItem(any(SqlQuerySpec.class), any(PartitionKey.class), any());
        doReturn(mockIterable).when(mockContainer).queryItems(anyString(), any(), any());
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(receipt);

        Receipt result = Assertions.assertDoesNotThrow(() -> sut.getReceiptDocument(RECEIPT_ID));

        assertEquals(RECEIPT_ID, result.getId());
    }

    /**
     * readItem lancia CosmosException 404 → fallback su queryItems → documento non trovato → ReceiptNotFoundException
     */
    @Test
    void getReceiptDocument_readItem404_fallbackToQuery_notFound_throwsReceiptNotFoundException() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockCosmosException.getStatusCode()).thenReturn(404);
        doThrow(mockCosmosException).when(mockContainer).readItem(any(SqlQuerySpec.class), any(PartitionKey.class), any());
        doReturn(mockIterable).when(mockContainer).queryItems(anyString(), any(), any());
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockIterator.hasNext()).thenReturn(false);

        assertThrows(ReceiptNotFoundException.class, () -> sut.getReceiptDocument("an invalid receipt id"));
    }

    /**
     * readItem lancia CosmosException con status != 404 → eccezione rilanciata
     */
    @Test
    void getReceiptDocument_readItemNon404CosmosException_rethrows() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockCosmosException.getStatusCode()).thenReturn(500);
        doThrow(mockCosmosException).when(mockContainer).readItem(anyString(), any(PartitionKey.class), any());

        assertThrows(CosmosException.class, () -> sut.getReceiptDocument(RECEIPT_ID));
    }

}
