package it.gov.pagopa.receipt.pdf.generator.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;

import java.util.List;

/**
 * Client for the CosmosDB database
 */
public class ReceiptCosmosClientImpl implements ReceiptCosmosClient {

    private final String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
    private final String containerId = System.getenv("COSMOS_RECEIPT_CONTAINER_NAME");

    private final CosmosClient cosmosClient;

    private ReceiptCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");
        String readRegion = System.getenv("COSMOS_RECEIPT_READ_REGION");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .preferredRegions(List.of(readRegion))
                .buildClient();
    }

    ReceiptCosmosClientImpl(CosmosClient cosmosClient) {
        this.cosmosClient = cosmosClient;
    }

    /**
     * Bill Pugh singleton holder: the JVM guarantees that the class is loaded
     * (and therefore INSTANCE initialized) lazily and in a thread-safe way.
     */
    private static class SingletonHelper {
        private static final ReceiptCosmosClientImpl INSTANCE = new ReceiptCosmosClientImpl();
    }

    public static ReceiptCosmosClientImpl getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Receipt getReceiptDocument(String eventId) throws ReceiptNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        try {
            return cosmosContainer
                    .readItem(eventId, new PartitionKey(eventId), Receipt.class)
                    .getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() != 404) {
                throw e;
            }
        }

        //Build query
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.eventId = @eventId",
                List.of(new SqlParameter("@eventId", eventId))
        );

        //Query the container
        return cosmosContainer
                .queryItems(querySpec, new CosmosQueryRequestOptions(), Receipt.class)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ReceiptNotFoundException("Document not found in the defined container"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<Receipt> updateReceipt(Receipt receipt) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        return cosmosContainer.upsertItem(receipt);
    }
}
