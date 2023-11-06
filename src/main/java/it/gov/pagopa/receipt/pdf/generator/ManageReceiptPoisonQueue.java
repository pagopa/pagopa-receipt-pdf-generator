package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToQueueException;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class ManageReceiptPoisonQueue {

    private final Logger logger = LoggerFactory.getLogger(ManageReceiptPoisonQueue.class);

    /**
     * This function will be invoked when a Queue trigger occurs
     *
     * Checks the queue message if it is a valid BizEvent event, and does not contains the attemptedPoisonRetry at true
     * If it is valid and not retried updates the field and sends it back to the original queue
     * If invalid or already retried saves on CosmosDB receipt-message-errors collection
     *
     * @param errorMessage payload of the message sent to the poison queue, triggering the function
     * @param documentdb      Output binding that will insert/update data with the errors not to retry within the function
     * @param context         Function context
     */
    @FunctionName("ManageReceiptPoisonQueueProcessor")
    public void processManageReceiptPoisonQueue(
            @QueueTrigger(
                    name = "QueueReceiptWaitingForGenPoison",
                    queueName = "%RECEIPT_QUEUE_TOPIC_POISON%",
                    connection = "RECEIPTS_STORAGE_CONN_STRING")
            String errorMessage,
            @CosmosDBOutput(
                    name = "ReceiptMessageErrorsDatastore",
                    databaseName = "db",
                    collectionName = "receipts-message-errors",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<ReceiptError> documentdb,
            final ExecutionContext context) {

        BizEvent bizEvent = null;

        logger.info("[{}] function called at {} for payload {}", context.getFunctionName(), LocalDateTime.now(), errorMessage);
        boolean retriableContent = false;

        try {
            //attempt to Map queue bizEventMessage to BizEvent
            bizEvent = ObjectMapperUtils.mapString(errorMessage, BizEvent.class);
             logger.info("[{}] function called at {} recognized as valid BizEvent with id {}",
                     context.getFunctionName(), LocalDateTime.now(), bizEvent.getId());
            if (bizEvent.getAttemptedPoisonRetry()) {
                 logger.info("[{}] function called at {} for event with id {} has ingestion already retried, sending to review",
                         context.getFunctionName(), LocalDateTime.now(), bizEvent.getId());
            } else {
                retriableContent = true;
            }
        } catch (JsonProcessingException e) {
             logger.error("[{}] received parsing error in the function called at {} for payload {}",
                     context.getFunctionName(), LocalDateTime.now(), errorMessage, e);
        }

        if (retriableContent) {
            bizEvent.setAttemptedPoisonRetry(true);
            ReceiptQueueClientImpl queueService = ReceiptQueueClientImpl.getInstance();
            try {
                Response<SendMessageResult> sendMessageResult =
                        queueService.sendMessageToQueue(Base64.getMimeEncoder()
                                .encodeToString(Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(bizEvent))
                                        .getBytes()));
                if (sendMessageResult.getStatusCode() != HttpStatus.CREATED.value()) {
                    throw new UnableToQueueException("Unable to queue due to error: " +
                            sendMessageResult.getStatusCode());
                }
            } catch (Exception e) {
                 logger.error("[{}] error for the function called at {} when attempting" +
                                 "to requeue BizEvent wit id {}, saving to cosmos for review",
                         context.getFunctionName(), LocalDateTime.now(), bizEvent.getId(), e);
                saveToDocument(context, errorMessage, documentdb);
            }
        } else {
            saveToDocument(context, errorMessage, documentdb);
        }
    }

    private void saveToDocument(ExecutionContext context, String errorMessage,
                                OutputBinding<ReceiptError> documentdb) {
         logger.info("[{}] saving new entry to the retry error to review with payload {}",
                 context.getFunctionName(), errorMessage);
        documentdb.setValue(ReceiptError.builder().messagePayload(Base64.getMimeEncoder()
                        .encodeToString(errorMessage.getBytes()))
                .status(ReceiptErrorStatusType.TO_REVIEW).build());
    }
}
