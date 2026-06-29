package it.gov.pagopa.receipt.pdf.generator.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.implementation.NotFoundException;
import com.azure.cosmos.models.CosmosItemResponse;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

@ExtendWith(MockitoExtension.class)
class CartReceiptsCosmosClientImplTest {

    private static final String CART_ID = "1";

    @Mock
    private CosmosClient cosmosClientMock;

    @Mock
    private CosmosDatabase mockDatabase;
    @Mock
    private CosmosContainer mockContainer;
    @Mock
    private CosmosItemResponse<CartForReceipt> mockItemResponse;

    @InjectMocks
    private CartReceiptsCosmosClientImpl sut;

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_RECEIPT_KEY", mockKey,
                "COSMOS_RECEIPT_SERVICE_ENDPOINT", "",
                "COSMOS_RECEIPT_READ_REGION", ""
        ).execute(() -> assertThrows(IllegalArgumentException.class, CartReceiptsCosmosClientImpl::getInstance)
        );
    }

    @Test
    void getCartItemSuccess() {
        CartForReceipt cartForReceipt = new CartForReceipt();
        cartForReceipt.setId(CART_ID);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.readItem(anyString(), any(), eq(CartForReceipt.class)))
                .thenReturn(mockItemResponse);
        when(mockItemResponse.getItem()).thenReturn(cartForReceipt);

        CartForReceipt result = assertDoesNotThrow(() -> sut.getCartItem(CART_ID));

        assertEquals(CART_ID, result.getId());
    }

    @Test
    void getCartItemFail() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.readItem(anyString(), any(), eq(CartForReceipt.class)))
                .thenReturn(mockItemResponse);
        when(mockItemResponse.getItem()).thenThrow(NotFoundException.class);

        assertThrows(CartNotFoundException.class, () -> sut.getCartItem("an invalid receipt id"));
    }

    @Test
    void updateCartSuccess() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);

        assertDoesNotThrow(() -> sut.updateCart(any()));

        verify(mockContainer).upsertItem(any());
    }
}