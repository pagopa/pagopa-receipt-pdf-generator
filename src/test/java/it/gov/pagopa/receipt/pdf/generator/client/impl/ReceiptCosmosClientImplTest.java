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

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private Stream<Receipt> mockReceiptStream;
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
                "COSMOS_RECEIPT_READ_REGION", "")
                .execute(() -> Assertions.assertThrows(ExceptionInInitializerError.class, ReceiptCosmosClientImpl::getInstance));
    }

    @Test
    void getReceiptDocument_OK_pointRead() {
        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.readItem(anyString(), any(PartitionKey.class), eq(Receipt.class))).thenReturn(mockItemResponse);
        when(mockItemResponse.getItem()).thenReturn(receipt);

        Receipt result = assertDoesNotThrow(() -> sut.getReceiptDocument(RECEIPT_ID));

        assertNotNull(result);
        assertEquals(RECEIPT_ID, result.getId());

        verify(mockContainer, never()).queryItems(anyString(), any(), eq(Receipt.class));
    }

    @Test
    void getReceiptDocument_OK_fallbackOnIterate() {
        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockCosmosException.getStatusCode()).thenReturn(404);
        when(mockContainer.readItem(anyString(), any(PartitionKey.class), eq(Receipt.class))).thenThrow(mockCosmosException);
        when(mockContainer.queryItems(any(SqlQuerySpec.class), any(), eq(Receipt.class))).thenReturn(mockIterable);
        when(mockIterable.stream()).thenReturn(mockReceiptStream);
        when(mockReceiptStream.findFirst()).thenReturn(Optional.of(receipt));

        Receipt result = assertDoesNotThrow(() -> sut.getReceiptDocument(RECEIPT_ID));

        assertEquals(RECEIPT_ID, result.getId());
    }

    @Test
    void getReceiptDocument_readItemNullFallbackToQuery_notFound_throwsReceiptNotFoundException() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockCosmosException.getStatusCode()).thenReturn(404);
        when(mockContainer.readItem(anyString(), any(PartitionKey.class), eq(Receipt.class))).thenThrow(mockCosmosException);
        when(mockContainer.queryItems(any(SqlQuerySpec.class), any(), eq(Receipt.class))).thenReturn(mockIterable);
        when(mockIterable.stream()).thenReturn(mockReceiptStream);
        when(mockReceiptStream.findFirst()).thenReturn(Optional.empty());

        assertThrows(ReceiptNotFoundException.class, () -> sut.getReceiptDocument("an invalid receipt id"));
    }

    @Test
    void getReceiptDocument_readItemNon404CosmosException_rethrows() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockCosmosException.getStatusCode()).thenReturn(500);
        when(mockContainer.readItem(anyString(), any(PartitionKey.class), eq(Receipt.class))).thenThrow(mockCosmosException);

        assertThrows(CosmosException.class, () -> sut.getReceiptDocument(RECEIPT_ID));
    }
}
