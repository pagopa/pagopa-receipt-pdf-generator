package it.gov.pagopa.receipt.pdf.generator.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.generator.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotFoundException;

import java.util.Arrays;
import java.util.List;

/**
 * Client for the CosmosDB database
 */
public class BizEventCosmosClientImpl implements BizEventCosmosClient {

    private static BizEventCosmosClientImpl instance;

    private final String databaseId = System.getenv("COSMOS_BIZ_EVENT_DB_NAME");
    private final String containerId = System.getenv("COSMOS_BIZ_EVENT_CONTAINER_NAME");

    private final CosmosClient cosmosClient;

    private BizEventCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_BIZ_EVENT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_BIZ_EVENT_SERVICE_ENDPOINT");
        String readRegion = System.getenv("COSMOS_BIZ_EVENT_READ_REGION");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .preferredRegions(List.of(readRegion))
                .buildClient();
    }

    public BizEventCosmosClientImpl(CosmosClient cosmosClient) {
        this.cosmosClient = cosmosClient;
    }

    public static BizEventCosmosClientImpl getInstance() {
        if (instance == null) {
            instance = new BizEventCosmosClientImpl();
        }
        return instance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BizEvent getBizEventDocument(String eventId) throws BizEventNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        //Build query
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c " +
                        "WHERE c.eventStatus IN (@statusDone, @statusIngested) " +
                        "  AND c.id = @id ",
                Arrays.asList(
                        new SqlParameter("@statusDone", BizEventStatusType.DONE),
                        new SqlParameter("@statusIngested", BizEventStatusType.INGESTED),
                        new SqlParameter("@id", eventId)
                )
        );

        //Query the container
        CosmosPagedIterable<BizEvent> queryResponse = cosmosContainer
                .queryItems(querySpec, new CosmosQueryRequestOptions(), BizEvent.class);

        if (queryResponse.iterator().hasNext()) {
            return queryResponse.iterator().next();
        }
        throw new BizEventNotFoundException("Document not found in the defined container");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BizEvent> getAllCartBizEventDocument(String transactionId) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        //Build query
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.transactionDetails.transaction.transactionId = @transactionId",
                List.of(
                        new SqlParameter("@transactionId", transactionId)
                )
        );

        //Query the container
        return cosmosContainer
                .queryItems(querySpec, new CosmosQueryRequestOptions(), BizEvent.class)
                .stream().limit(6)
                .toList();
    }

}
