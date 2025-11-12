package it.gov.pagopa.receipt.pdf.generator.service.impl;

import com.azure.cosmos.models.CosmosItemResponse;
import it.gov.pagopa.receipt.pdf.generator.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.CartReceiptsCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToSaveException;
import it.gov.pagopa.receipt.pdf.generator.service.CartReceiptCosmosService;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CartReceiptCosmosServiceImpl implements CartReceiptCosmosService {
    private final Logger logger = LoggerFactory.getLogger(CartReceiptCosmosServiceImpl.class);
    private final CartReceiptsCosmosClient cartReceiptsCosmosClient;

    public CartReceiptCosmosServiceImpl() {
        this.cartReceiptsCosmosClient = CartReceiptsCosmosClientImpl.getInstance();
    }

    public CartReceiptCosmosServiceImpl(CartReceiptsCosmosClient cartReceiptsCosmosClient) {
        this.cartReceiptsCosmosClient = cartReceiptsCosmosClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateCartForReceipt(CartForReceipt cartForReceipt) throws UnableToSaveException {
        int statusCode;

        try {
            CosmosItemResponse<CartForReceipt> response = this.cartReceiptsCosmosClient.updateCart(cartForReceipt);
            statusCode = response.getStatusCode();
        } catch (Exception e) {
            statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
            logger.error(String.format("Save cart receipt with eventId %s on cosmos failed", cartForReceipt.getEventId()), e);
        }

        if (statusCode != com.microsoft.azure.functions.HttpStatus.OK.value()) {
            String errorMsg = String.format("Save cart receipt with eventId %s on cosmos failed with status %s", cartForReceipt.getEventId(), statusCode);
            throw new UnableToSaveException(errorMsg);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CartForReceipt getCartForReceipt(String cartId) throws CartNotFoundException {
        CartForReceipt cart;
        try {
            cart = this.cartReceiptsCosmosClient.getCartItem(cartId);
        } catch (CartNotFoundException e) {
            String errorMsg = String.format("CartForReceipt not found with the event id %s", cartId);
            throw new CartNotFoundException(errorMsg, e);
        }

        if (cart == null) {
            String errorMsg = String.format("CartForReceipt retrieved with the event id %s is null", cartId);
            throw new CartNotFoundException(errorMsg);
        }
        return cart;
    }
}
