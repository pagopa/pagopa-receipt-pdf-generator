package it.gov.pagopa.receipt.pdf.generator.client.impl;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.generator.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;

import java.util.List;

public class CartReceiptsCosmosClientImpl implements CartReceiptsCosmosClient {

    private static CartReceiptsCosmosClientImpl instance;
    private final String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
    private final String cartForReceiptContainerName = System.getenv("CART_FOR_RECEIPT_CONTAINER_NAME");

    private final CosmosClient cosmosClient;

    private CartReceiptsCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");
        String readRegion = System.getenv("COSMOS_RECEIPT_READ_REGION");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .consistencyLevel(ConsistencyLevel.BOUNDED_STALENESS)
                .preferredRegions(List.of(readRegion))
                .buildClient();
    }

    public CartReceiptsCosmosClientImpl(CosmosClient cosmosClient) {
        this.cosmosClient = cosmosClient;
    }

    public static CartReceiptsCosmosClientImpl getInstance() {
        if (instance == null) {
            instance = new CartReceiptsCosmosClientImpl();
        }

        return instance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CartForReceipt getCartItem(String eventId) throws CartNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);

        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);

        //Build query
        String query = "SELECT * FROM c WHERE c.eventId = '%s'".formatted(eventId);

        //Query the container
        CosmosPagedIterable<CartForReceipt> queryResponse = cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), CartForReceipt.class);

        if (queryResponse.iterator().hasNext()) {
            return queryResponse.iterator().next();
        } else {
            throw new CartNotFoundException("Document not found in the defined container");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<CartForReceipt> updateCart(CartForReceipt receipt) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);
        return cosmosContainer.upsertItem(receipt);
    }
}
