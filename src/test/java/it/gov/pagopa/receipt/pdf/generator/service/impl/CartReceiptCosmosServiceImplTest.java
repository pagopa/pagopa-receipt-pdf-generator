package it.gov.pagopa.receipt.pdf.generator.service.impl;

import com.azure.cosmos.models.CosmosItemResponse;
import it.gov.pagopa.receipt.pdf.generator.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToSaveException;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class CartReceiptCosmosServiceImplTest {

    @Mock
    private CartReceiptsCosmosClient cartCosmosClientMock;

    @Mock
    private CosmosItemResponse<CartForReceipt> responseMock;

    @InjectMocks
    private CartReceiptCosmosServiceImpl sut;

    @Test
    void updateCartForReceiptSuccess() {
        doReturn(responseMock).when(cartCosmosClientMock).updateCart(any());
        doReturn(HttpStatus.SC_OK).when(responseMock).getStatusCode();

        assertDoesNotThrow(() -> sut.updateCartForReceipt(new CartForReceipt()));
    }

    @Test
    void updateCartForReceiptFailUpdateResponseStatusNotOK() {
        doReturn(responseMock).when(cartCosmosClientMock).updateCart(any());
        doReturn(HttpStatus.SC_INTERNAL_SERVER_ERROR).when(responseMock).getStatusCode();

        assertThrows(UnableToSaveException.class, () -> sut.updateCartForReceipt(new CartForReceipt()));
    }

    @Test
    void updateCartForReceiptFailUpdateResponseThrowsException() {
        doThrow(RuntimeException.class).when(cartCosmosClientMock).updateCart(any());

        assertThrows(UnableToSaveException.class, () -> sut.updateCartForReceipt(new CartForReceipt()));
    }

    @Test
    void getCartForReceiptSuccess() throws CartNotFoundException {
        doReturn(new CartForReceipt()).when(cartCosmosClientMock).getCartItem(anyString());

        CartForReceipt result = assertDoesNotThrow(() -> sut.getCartForReceipt(anyString()));

        assertNotNull(result);
    }

    @Test
    void getCartForReceiptFailNotFound() throws CartNotFoundException {
        doThrow(CartNotFoundException.class).when(cartCosmosClientMock).getCartItem(anyString());

        CartNotFoundException e = assertThrows(CartNotFoundException.class, () -> sut.getCartForReceipt(anyString()));

        assertNotNull(e);
    }

    @Test
    void getCartForReceiptFailNullDocument() throws CartNotFoundException {
        doReturn(null).when(cartCosmosClientMock).getCartItem(anyString());

        CartNotFoundException e = assertThrows(CartNotFoundException.class, () -> sut.getCartForReceipt(anyString()));

        assertNotNull(e);
    }

}