package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptQueueClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.Aes256Exception;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToQueueException;
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.service.impl.ReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.utils.Aes256Utils;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import it.gov.pagopa.receipt.pdf.generator.utils.ReceiptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class ManageReceiptPoisonQueue {

    private final Logger logger = LoggerFactory.getLogger(ManageReceiptPoisonQueue.class);

    private final ReceiptCosmosService receiptCosmosService;
    private final ReceiptQueueClient queueService;

    public ManageReceiptPoisonQueue() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.queueService = ReceiptQueueClientImpl.getInstance();
    }

    ManageReceiptPoisonQueue(ReceiptCosmosService receiptCosmosService, ReceiptQueueClient receiptQueueClient) {
        this.receiptCosmosService = receiptCosmosService;
        this.queueService = receiptQueueClient;
    }

    /**
     * This function will be invoked when a Queue trigger occurs
     *
     * Checks the queue message if it is a valid BizEvent event, and does not contains the attemptedPoisonRetry at true
     * If it is valid and not retried updates the field and sends it back to the original queue
     * If invalid or already retried saves on CosmosDB receipt-message-errors collection
     *
     * @param errorMessage              payload of the message sent to the poison queue, triggering the function
     * @param receiptsOutputBinding     Output binding that will update the receipt relative to the bizEvent
     * @param receiptErrorOutputBinding Output binding that will insert/update data with the errors not to retry within the function
     * @param context                   Function context
     */
    @FunctionName("ManageReceiptPoisonQueueProcessor")
    public void processManageReceiptPoisonQueue(
            @QueueTrigger(
                    name = "QueueReceiptWaitingForGenPoison",
                    queueName = "%RECEIPT_QUEUE_TOPIC_POISON%",
                    connection = "RECEIPTS_STORAGE_CONN_STRING")
            String errorMessage,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<Receipt> receiptsOutputBinding,
            @CosmosDBOutput(
                    name = "ReceiptMessageErrorsDatastore",
                    databaseName = "db",
                    collectionName = "receipts-message-errors",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<ReceiptError> receiptErrorOutputBinding,
            final ExecutionContext context) {

        List<BizEvent> listOfBizEvents = new ArrayList<>();
        BizEvent bizEvent = null;

        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());
        boolean retriableContent = false;

        try {
            //attempt to Map queue bizEventMessage to BizEvent
            listOfBizEvents = ObjectMapperUtils.mapBizEventListString(errorMessage, new TypeReference<>() {});
            bizEvent = listOfBizEvents.get(0);
            logger.info("[{}] function called at {} recognized as valid BizEvent with id {}",
                    context.getFunctionName(), LocalDateTime.now(), bizEvent.getId());
            if (Boolean.TRUE.equals(bizEvent.getAttemptedPoisonRetry())) {
                logger.info("[{}] function called at {} for event with id {} has ingestion already retried, sending to review",
                        context.getFunctionName(), LocalDateTime.now(), bizEvent.getId());
            } else {
                retriableContent = true;
            }
        } catch (JsonProcessingException e) {
            logger.error("[{}] received parsing error in the function called at {}",
                    context.getFunctionName(), LocalDateTime.now(), e);
        }

        if (retriableContent) {
            bizEvent.setAttemptedPoisonRetry(true);
            try {
                Response<SendMessageResult> sendMessageResult =
                        this.queueService.sendMessageToQueue(Base64.getMimeEncoder()
                                .encodeToString(Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(listOfBizEvents))
                                        .getBytes()));
                if (sendMessageResult.getStatusCode() != HttpStatus.CREATED.value()) {
                    throw new UnableToQueueException("Unable to queue due to error: " +
                            sendMessageResult.getStatusCode());
                }
            } catch (Exception e) {
                logger.error("[{}] error for the function called at {} when attempting" +
                                "to requeue BizEvent with id {}, saving to cosmos for review",
                        context.getFunctionName(), LocalDateTime.now(), bizEvent.getId(), e);
                saveReceiptErrorAndUpdateReceipt(errorMessage, receiptsOutputBinding, receiptErrorOutputBinding, context, bizEvent, listOfBizEvents.size() > 1);
            }
        } else {
            saveReceiptErrorAndUpdateReceipt(errorMessage, receiptsOutputBinding, receiptErrorOutputBinding, context, bizEvent, listOfBizEvents.size() > 1);
        }
    }

    private void saveReceiptErrorAndUpdateReceipt(String errorMessage, OutputBinding<Receipt> receiptsOutputBinding, OutputBinding<ReceiptError> receiptErrorOutputBinding, ExecutionContext context, BizEvent bizEvent, boolean isMultiItem) {
        String bizEventReference = ReceiptUtils.getReceiptEventReference(bizEvent, isMultiItem);
        saveToReceiptError(context, errorMessage, bizEventReference, receiptErrorOutputBinding);
        if (bizEventReference != null) {
            updateReceiptToReview(context, bizEventReference, receiptsOutputBinding);
        }
    }

    private void saveToReceiptError(ExecutionContext context, String errorMessage, String bizEventId,
                                    OutputBinding<ReceiptError> receiptErrorOutputBinding) {

        ReceiptError receiptError = ReceiptError.builder()
                .bizEventId(bizEventId)
                .status(ReceiptErrorStatusType.TO_REVIEW).build();

        try {
            String encodedEvent = Aes256Utils.encrypt(errorMessage);
            receiptError.setMessagePayload(encodedEvent);

            logger.info("[{}] saving new entry to the retry error to review with payload {}",
                    context.getFunctionName(), encodedEvent);
        } catch (Aes256Exception e) {
            receiptError.setMessageError(e.getMessage());
        }

        receiptErrorOutputBinding.setValue(receiptError);
    }

    private void updateReceiptToReview(ExecutionContext context, String eventId,
                                       OutputBinding<Receipt> receiptOutputBinding) {
        try {
            Receipt receipt = this.receiptCosmosService.getReceipt(eventId);

            receipt.setStatus(ReceiptStatusType.TO_REVIEW);

            logger.info("[{}] updating receipt with id {} to status {}",
                    context.getFunctionName(), receipt.getId(), ReceiptStatusType.TO_REVIEW);
            receiptOutputBinding.setValue(receipt);
        } catch (ReceiptNotFoundException e) {
            logger.error("[{}] error updating status of receipt with eventId {}, receipt not found",
                    context.getFunctionName(), eventId, e);
        }
    }
}