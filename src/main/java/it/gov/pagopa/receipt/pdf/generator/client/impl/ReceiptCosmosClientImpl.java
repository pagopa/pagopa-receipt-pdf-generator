package it.gov.pagopa.receipt.pdf.generator.client.impl;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
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

    private static class SingletonHelper {
        private static final ReceiptCosmosClientImpl RECEIPT_COSMOS_CLIENT_SINGLETON_INSTANCE = new ReceiptCosmosClientImpl();
    }

    public static ReceiptCosmosClientImpl getInstance() {
        return ReceiptCosmosClientImpl.SingletonHelper.RECEIPT_COSMOS_CLIENT_SINGLETON_INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Receipt getReceiptDocument(String id) throws ReceiptNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        try{
            CosmosItemResponse<Receipt> itemResponse = cosmosContainer
                    .readItem(id, new PartitionKey(id), Receipt.class);
            if (itemResponse != null) {
                return itemResponse.getItem();
            }
        } catch (CosmosException ce) {
            if (ce.getStatusCode() != 404) {
                // if not found use fallback query
                throw ce;
            }
        }

        //Build query
        String query = "SELECT * FROM c WHERE c.eventId = " + "'" + id + "'";

        //Query the container
        CosmosPagedIterable<Receipt> queryResponse = cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), Receipt.class);

        if (queryResponse.iterator().hasNext()) {
            return queryResponse.iterator().next();
        } else {
            throw new ReceiptNotFoundException("Document not found in the defined container");
        }

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
