package it.gov.pagopa.receipt.pdf.generator.client.impl;

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

    private final CosmosContainer receiptContainer;

    @SuppressWarnings("resource") // CosmosClient lifecycle == singleton lifecycle; never closed on purpose
    private ReceiptCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");
        String readRegion = System.getenv("COSMOS_RECEIPT_READ_REGION");

        String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
        String containerId = System.getenv("COSMOS_RECEIPT_CONTAINER_NAME");

        CosmosDatabase database = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .preferredRegions(List.of(readRegion))
                .buildClient()
                .getDatabase(databaseId);

        this.receiptContainer = database.getContainer(containerId);
    }

    /**
     * Test-only constructor. Package-private visibility so it is only reachable from tests
     * in the same package.
     */
    ReceiptCosmosClientImpl(CosmosContainer receiptContainer) {
        this.receiptContainer = receiptContainer;
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
        try {
            return receiptContainer
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
        return receiptContainer
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
        return receiptContainer.upsertItem(receipt);
    }
}
