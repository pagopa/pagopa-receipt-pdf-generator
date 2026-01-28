package it.gov.pagopa.receipt.pdf.generator.client;

import com.azure.cosmos.models.CosmosItemResponse;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;

public interface CartReceiptsCosmosClient {

    /**
     * Retrieve Cart For Receipt document from CosmosDB database
     *
     * @param cartId Biz-event transaction id, that identifies the cart
     * @return cart-for-receipts document
     * @throws CartNotFoundException in case no cart has been found with the given event id
     */
    CartForReceipt getCartItem(String cartId) throws CartNotFoundException;

    /**
     * Update Cart For Receipt on CosmosDB database
     *
     * @param receipt Cart Data to save
     * @return the updated cart-for-receipts document
     */
    CosmosItemResponse<CartForReceipt> updateCart(CartForReceipt receipt);
}
