package it.gov.pagopa.receipt.pdf.generator.service;

import it.gov.pagopa.receipt.pdf.generator.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToSaveException;

public interface CartReceiptCosmosService {

    /**
     * Saves the provided CartForReceipt object using {@link CartReceiptsCosmosClient}
     *
     * @param cartForReceipt the cart to save
     * @throws UnableToSaveException if an error occur during save
     */
    void updateCartForReceipt(CartForReceipt cartForReceipt) throws UnableToSaveException;

    /**
     * Recovers a cart from the CosmosDB by the property eventId
     *
     * @param cartId the cart identifier
     * @return the cart found
     * @throws CartNotFoundException when no cart has been found
     */
    CartForReceipt getCartForReceipt(String cartId) throws CartNotFoundException;
}
