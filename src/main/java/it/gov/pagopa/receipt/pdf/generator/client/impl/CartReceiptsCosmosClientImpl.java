package it.gov.pagopa.receipt.pdf.generator.client.impl;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.receipt.pdf.generator.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;

import java.util.List;

public class CartReceiptsCosmosClientImpl implements CartReceiptsCosmosClient {

    private final CosmosContainer cartForReceiptContainer;

    @SuppressWarnings("resource") // CosmosClient lifecycle == singleton lifecycle; never closed on purpose
    private CartReceiptsCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");
        String readRegion = System.getenv("COSMOS_RECEIPT_READ_REGION");

        String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
        String cartForReceiptContainerName = System.getenv("CART_FOR_RECEIPT_CONTAINER_NAME");

        CosmosDatabase database = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .consistencyLevel(ConsistencyLevel.BOUNDED_STALENESS)
                .preferredRegions(List.of(readRegion))
                .buildClient()
                .getDatabase(databaseId);

        this.cartForReceiptContainer = database.getContainer(cartForReceiptContainerName);
    }

    /**
     * Test-only constructor. Package-private visibility so it is only reachable from tests
     * in the same package.
     */
    CartReceiptsCosmosClientImpl(CosmosContainer cartForReceiptContainer) {
        this.cartForReceiptContainer = cartForReceiptContainer;
    }

    /**
     * Bill Pugh singleton holder: the JVM guarantees that the class is loaded
     * (and therefore INSTANCE initialized) lazily and in a thread-safe way.
     */
    private static class SingletonHelper {
        private static final CartReceiptsCosmosClientImpl INSTANCE = new CartReceiptsCosmosClientImpl();
    }

    public static CartReceiptsCosmosClientImpl getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CartForReceipt getCartItem(String cartId) throws CartNotFoundException {
        //Query the container
        try {
            return cartForReceiptContainer
                    .readItem(cartId, new PartitionKey(cartId), CartForReceipt.class)
                    .getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                throw new CartNotFoundException("Document not found in the defined container", e);
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<CartForReceipt> updateCart(CartForReceipt receipt) {
        return cartForReceiptContainer.upsertItem(receipt);
    }
}
