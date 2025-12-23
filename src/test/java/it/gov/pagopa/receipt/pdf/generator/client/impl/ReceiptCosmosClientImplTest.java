package it.gov.pagopa.receipt.pdf.generator.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private ReceiptCosmosClientImpl sut;

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_RECEIPT_KEY", mockKey,
                "COSMOS_RECEIPT_SERVICE_ENDPOINT", "",
                "COSMOS_RECEIPT_READ_REGION", ""
        ).execute(() -> Assertions.assertThrows(IllegalArgumentException.class, ReceiptCosmosClientImpl::getInstance)
        );
    }

    @Test
    void runOk() {
        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(mockIterable);
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(receipt);

        Receipt result = Assertions.assertDoesNotThrow(() -> sut.getReceiptDocument(RECEIPT_ID));

        assertEquals(RECEIPT_ID, result.getId());
    }

    @Test
    void runKo() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(mockIterable);
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockIterator.hasNext()).thenReturn(false);

        assertThrows(ReceiptNotFoundException.class, () -> sut.getReceiptDocument("an invalid receipt id"));
    }

}