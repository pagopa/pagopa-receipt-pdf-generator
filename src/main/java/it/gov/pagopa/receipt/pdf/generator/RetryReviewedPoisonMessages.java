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
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptQueueClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToQueueException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToSaveException;
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.service.impl.ReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.utils.Aes256Utils;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Azure Functions with Azure CosmosDB trigger.
 */
public class RetryReviewedPoisonMessages {

     private final Logger logger = LoggerFactory.getLogger(RetryReviewedPoisonMessages.class);

    private final ReceiptCosmosService receiptCosmosService;
    private final ReceiptQueueClient queueService;

    public RetryReviewedPoisonMessages() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.queueService = ReceiptQueueClientImpl.getInstance();
    }
    RetryReviewedPoisonMessages(ReceiptCosmosService receiptCosmosService, ReceiptQueueClient receiptQueueClient) {
        this.receiptCosmosService = receiptCosmosService;
        this.queueService = receiptQueueClient;
    }

    /**
     * This function will be invoked when an CosmosDB trigger occurs
     *
     * When an updated document in the receipt-message-errors CosmosDB has status REVIEWED attempts
     * to send it back to the provided output topic.
     * If succeeds saves the element with status REQUEUED and updated the relative receipt's status to INSERTED
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

        //Retrieve receipt data from biz-event
        for (ReceiptError receiptError : items) {

                //Process only errors in status REVIEWED
                if (receiptError != null && receiptError.getStatus().equals(ReceiptErrorStatusType.REVIEWED)) {

                    try {
                        String decodedEvent = Aes256Utils.decrypt(receiptError.getMessagePayload());

                        //Find and update Receipt with bizEventId
                        BizEvent bizEvent = ObjectMapperUtils.mapString(decodedEvent, BizEvent.class);
                        updateReceiptToInserted(context, bizEvent.getId());

                        //Send decoded BizEvent to queue
                        Response<SendMessageResult> sendMessageResult =
                            this.queueService.sendMessageToQueue(Base64.getMimeEncoder().encodeToString(decodedEvent.getBytes()));
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

        if(!itemsDone.isEmpty()){
            documentdb.setValue(itemsDone);
        }
    }

    private void updateReceiptToInserted(ExecutionContext context, String bizEventId) throws ReceiptNotFoundException, UnableToSaveException {
            Receipt receipt = this.receiptCosmosService.getReceipt(bizEventId);
            receipt.setStatus(ReceiptStatusType.INSERTED);
            logger.info("[{}] updating receipt with id {} to status {}", context.getFunctionName(), receipt.getId(), ReceiptStatusType.INSERTED);
            this.receiptCosmosService.saveReceipt(receipt);
    }
}
