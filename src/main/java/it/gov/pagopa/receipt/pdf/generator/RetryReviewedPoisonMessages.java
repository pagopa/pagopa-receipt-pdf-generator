package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToQueueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Azure Functions with Azure CosmosDB trigger.
 */
public class RetryReviewedPoisonMessages {

     private final Logger logger = LoggerFactory.getLogger(RetryReviewedPoisonMessages.class);

    /**
     * This function will be invoked when an CosmosDB trigger occurs
     *
     * When an updated document in the receipt-message-errors CosmosDB has status REVIEWED attempts
     * to send it back to the provided output topic.
     * If succeeds saves the element with status REQUEUED
     * If fails updates the document back in status TO_REVIEW with an updated error description
     *
     * @param items      Reviewed Receipt Errors that triggered the function from the Cosmos database
     * @param context    Function context
     */
    @FunctionName("RetryReviewedPoisonMessagesProcessor")
    @ExponentialBackoffRetry(maxRetryCount = 5, minimumInterval = "500", maximumInterval = "5000")
    public void processRetryReviewedPoisonMessages(
            @CosmosDBTrigger(
                    name = "ReceiptErrorDatastore",
                    databaseName = "db",
                    collectionName = "receipts-message-errors",
                    leaseCollectionName = "receipts-message-errors-leases",
                    leaseCollectionPrefix = "materialized",
                    createLeaseCollectionIfNotExists = true,
                    maxItemsPerInvocation = 100,
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            List<ReceiptError> items,
            @CosmosDBOutput(
                    name = "ReceiptMessageErrorsDatastoreOutput",
                    databaseName = "db",
                    collectionName = "receipts-message-errors",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<ReceiptError>> documentdb,
            final ExecutionContext context) {

        List<ReceiptError> itemsDone = new ArrayList<>();

         logger.info("[{}] documentCaptorValue stat {} function - num errors reviewed triggered {}",
                 context.getFunctionName(), context.getInvocationId(), items.size());

        ReceiptQueueClientImpl queueService = ReceiptQueueClientImpl.getInstance();

        //Retrieve receipt data from biz-event
        for (ReceiptError receiptError : items) {

                //Process only errors in status REVIEWED
                if (receiptError != null && receiptError.getStatus().equals(ReceiptErrorStatusType.REVIEWED)) {

                    try {

                        Response<SendMessageResult> sendMessageResult =
                            queueService.sendMessageToQueue(receiptError.getMessagePayload());
                        if (sendMessageResult.getStatusCode() != HttpStatus.CREATED.value()) {
                            throw new UnableToQueueException("Unable to queue due to error: " +
                                    sendMessageResult.getStatusCode());
                        }

                        receiptError.setStatus(ReceiptErrorStatusType.REQUEUED);

                    } catch (Exception e) {
                        //Error info
                         logger.error("[{}] Error to process receiptError with id {}",
                                 context.getFunctionName(), receiptError.getId(), e);
                        receiptError.setMessageError(e.getMessage());
                        receiptError.setStatus(ReceiptErrorStatusType.TO_REVIEW);
                    }

                    itemsDone.add(receiptError);
               }
        }

        documentdb.setValue(itemsDone);
    }
}
