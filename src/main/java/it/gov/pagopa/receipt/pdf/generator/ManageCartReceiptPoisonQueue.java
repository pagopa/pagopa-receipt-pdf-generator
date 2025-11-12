package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.receipt.pdf.generator.client.CartQueueClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.CartQueueClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.Aes256Exception;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToQueueException;
import it.gov.pagopa.receipt.pdf.generator.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.service.impl.CartReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.utils.Aes256Utils;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import it.gov.pagopa.receipt.pdf.generator.utils.ReceiptGeneratorUtils;
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
public class ManageCartReceiptPoisonQueue {

    private final Logger logger = LoggerFactory.getLogger(ManageCartReceiptPoisonQueue.class);

    private final CartReceiptCosmosService cartReceiptCosmosService;
    private final CartQueueClient cartQueueClient;

    public ManageCartReceiptPoisonQueue() {
        this.cartReceiptCosmosService = new CartReceiptCosmosServiceImpl();
        this.cartQueueClient = CartQueueClientImpl.getInstance();
    }

    ManageCartReceiptPoisonQueue(CartReceiptCosmosService cartReceiptCosmosService, CartQueueClient cartQueueClient) {
        this.cartReceiptCosmosService = cartReceiptCosmosService;
        this.cartQueueClient = cartQueueClient;
    }

    /**
     * This function will be invoked when a Queue trigger occurs
     * <p>
     * Checks the queue message if it is a valid BizEvent event, and does not contain the attemptedPoisonRetry at true
     * If it is valid and not retried updates the field and sends it back to the original queue
     * If invalid or already retried saves on CosmosDB cart-receipt-message-errors collection
     *
     * @param errorMessage                  payload of the message sent to the poison queue, triggering the function
     * @param cartReceiptsOutputBinding     Output binding that will update the cart-receipt relative to the bizEvent
     * @param cartReceiptErrorOutputBinding Output binding that will insert/update data with the errors not to retry within the function
     * @param context                       Function context
     */
    @FunctionName("ManageCartReceiptPoisonQueueProcessor")
    public void processManageCartReceiptPoisonQueue(
            @QueueTrigger(
                    name = "QueueCartReceiptWaitingForGenPoison",
                    queueName = "%CART_RECEIPT_QUEUE_TOPIC_POISON%",
                    connection = "RECEIPTS_STORAGE_CONN_STRING")
            String errorMessage,
            @CosmosDBOutput(
                    name = "CartReceiptDatastore",
                    databaseName = "db",
                    containerName = "cart-for-receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<CartForReceipt> cartReceiptsOutputBinding,
            @CosmosDBOutput(
                    name = "CartReceiptMessageErrorsDatastore",
                    databaseName = "db",
                    containerName = "cart-receipts-message-errors",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<CartReceiptError> cartReceiptErrorOutputBinding,
            final ExecutionContext context) {

        List<BizEvent> listOfBizEvent = new ArrayList<>();
        BizEvent bizEvent = null;

        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());
        boolean retriableContent = false;

        try {
            //attempt to Map queue bizEventMessage to BizEvent
            listOfBizEvent = ReceiptGeneratorUtils.getBizEventListFromMessage(errorMessage, context.getFunctionName());
            bizEvent = listOfBizEvent.get(0);
            logger.debug("[{}] function called at {} recognized as valid BizEvent with id {}",
                    context.getFunctionName(), LocalDateTime.now(), bizEvent.getId());
            if (Boolean.TRUE.equals(bizEvent.getAttemptedPoisonRetry())) {
                logger.info("[{}] function called at {} for event with id {} has ingestion already retried, sending to review",
                        context.getFunctionName(), LocalDateTime.now(), bizEvent.getId());
            } else {
                retriableContent = true;
            }
        } catch (BizEventNotValidException e) {
            logger.error("[{}] received parsing error in the function called at {}",
                    context.getFunctionName(), LocalDateTime.now(), e);
        }

        if (retriableContent) {
            bizEvent.setAttemptedPoisonRetry(true);
            try {
                Response<SendMessageResult> sendMessageResult =
                        this.cartQueueClient.sendMessageToQueue(Base64.getMimeEncoder()
                                .encodeToString(Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(listOfBizEvent))
                                        .getBytes()));
                if (sendMessageResult.getStatusCode() != HttpStatus.CREATED.value()) {
                    throw new UnableToQueueException("Unable to queue due to error: " +
                            sendMessageResult.getStatusCode());
                }
            } catch (Exception e) {
                logger.error("[{}] error for the function called at {} when attempting" +
                                "to requeue BizEvent with id {}, saving to cosmos for review",
                        context.getFunctionName(), LocalDateTime.now(), bizEvent.getId(), e);
                saveReceiptErrorAndUpdateReceipt(errorMessage, cartReceiptsOutputBinding, cartReceiptErrorOutputBinding, context, bizEvent);
            }
        } else {
            saveReceiptErrorAndUpdateReceipt(errorMessage, cartReceiptsOutputBinding, cartReceiptErrorOutputBinding, context, bizEvent);
        }
    }

    private void saveReceiptErrorAndUpdateReceipt(String errorMessage, OutputBinding<CartForReceipt> receiptsOutputBinding, OutputBinding<CartReceiptError> receiptErrorOutputBinding, ExecutionContext context, BizEvent bizEvent) {
        String bizEventReference = ReceiptGeneratorUtils.getCartReceiptEventReference(bizEvent);
        saveToReceiptError(context, errorMessage, bizEventReference, receiptErrorOutputBinding);
        if (bizEventReference != null) {
            updateReceiptToReview(context, bizEventReference, receiptsOutputBinding);
        }
    }

    private void saveToReceiptError(ExecutionContext context, String errorMessage, String cartId,
                                    OutputBinding<CartReceiptError> receiptErrorOutputBinding) {

        CartReceiptError receiptError = CartReceiptError.builder()
                .id(cartId)
                .status(ReceiptErrorStatusType.TO_REVIEW).build();

        try {
            String encodedEvent = Aes256Utils.encrypt(errorMessage);
            receiptError.setMessagePayload(encodedEvent);

            logger.debug("[{}] saving new entry to the retry error to review with payload {}",
                    context.getFunctionName(), encodedEvent);
        } catch (Aes256Exception e) {
            receiptError.setMessageError(e.getMessage());
        }

        receiptErrorOutputBinding.setValue(receiptError);
    }

    private void updateReceiptToReview(ExecutionContext context, String cartId,
                                       OutputBinding<CartForReceipt> receiptOutputBinding) {
        try {
            CartForReceipt receipt = this.cartReceiptCosmosService.getCartForReceipt(cartId);

            receipt.setStatus(CartStatusType.TO_REVIEW);

            logger.debug("[{}] updating cart-receipt with id {} to status {}",
                    context.getFunctionName(), receipt.getId(), receipt.getStatus());
            receiptOutputBinding.setValue(receipt);
        } catch (CartNotFoundException e) {
            logger.error("[{}] error updating status of cart-receipt with cartId {}, cart-receipt not found",
                    context.getFunctionName(), cartId, e);
        }
    }
}