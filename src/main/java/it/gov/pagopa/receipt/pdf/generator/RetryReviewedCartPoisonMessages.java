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
import it.gov.pagopa.receipt.pdf.generator.client.CartQueueClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.CartQueueClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToQueueException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToSaveException;
import it.gov.pagopa.receipt.pdf.generator.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.service.impl.CartReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.utils.Aes256Utils;
import it.gov.pagopa.receipt.pdf.generator.utils.ReceiptGeneratorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Azure Functions with Azure CosmosDB trigger.
 */
public class RetryReviewedCartPoisonMessages {

    private final Logger logger = LoggerFactory.getLogger(RetryReviewedCartPoisonMessages.class);

    private final CartReceiptCosmosService cartReceiptCosmosService;
    private final CartQueueClient cartQueueClient;

    public RetryReviewedCartPoisonMessages() {
        this.cartReceiptCosmosService = new CartReceiptCosmosServiceImpl();
        this.cartQueueClient = CartQueueClientImpl.getInstance();
    }

    RetryReviewedCartPoisonMessages(CartReceiptCosmosService cartReceiptCosmosService, CartQueueClient cartQueueClient) {
        this.cartReceiptCosmosService = cartReceiptCosmosService;
        this.cartQueueClient = cartQueueClient;
    }

    /**
     * This function will be invoked when an CosmosDB trigger occurs
     * <p>
     * When an updated document in the cart-receipt-message-errors CosmosDB has status REVIEWED attempts
     * to send it back to the provided output topic.
     * If succeeds saves the element with status REQUEUED and updated the relative cart-receipt's status to INSERTED
     * If fails updates the document back in status TO_REVIEW with an updated error description
     *
     * @param items   Reviewed Cart-Receipt Errors that triggered the function from the Cosmos database
     * @param context Function context
     */
    @FunctionName("RetryReviewedCartPoisonMessagesProcessor")
    @ExponentialBackoffRetry(maxRetryCount = 5, minimumInterval = "500", maximumInterval = "5000")
    public void processRetryReviewedCartPoisonMessages(
            @CosmosDBTrigger(
                    name = "CartReceiptErrorDatastore",
                    databaseName = "db",
                    containerName = "cart-receipts-message-errors",
                    leaseContainerName = "cart-receipts-message-errors-leases",
                    leaseContainerPrefix = "materialized",
                    createLeaseContainerIfNotExists = true,
                    maxItemsPerInvocation = 100,
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            List<CartReceiptError> items,
            @CosmosDBOutput(
                    name = "CartReceiptMessageErrorsDatastoreOutput",
                    databaseName = "db",
                    containerName = "cart-receipts-message-errors",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<CartReceiptError>> documentdb,
            final ExecutionContext context) {

        List<CartReceiptError> itemsDone = new ArrayList<>();

        logger.info("[{}] documentCaptorValue stat {} function - num errors reviewed triggered {}",
                context.getFunctionName(), context.getInvocationId(), items.size());

        //Retrieve receipt data from biz-event
        for (CartReceiptError cartReceiptError : items) {

            //Process only errors in status REVIEWED
            if (cartReceiptError != null && cartReceiptError.getStatus().equals(ReceiptErrorStatusType.REVIEWED)) {

                try {
                    String decodedEventList = Aes256Utils.decrypt(cartReceiptError.getMessagePayload());

                    //Find and update Receipt with bizEventId
                    List<BizEvent> listOfBizEvents = ReceiptGeneratorUtils.getBizEventListFromMessage(decodedEventList, context.getFunctionName());
                    String receiptEventReference = ReceiptGeneratorUtils.getCartReceiptEventReference(listOfBizEvents.get(0));
                    updateReceiptToInserted(context, receiptEventReference);

                    //Send decoded BizEvent to queue
                    Response<SendMessageResult> sendMessageResult =
                            this.cartQueueClient.sendMessageToQueue(Base64.getMimeEncoder().encodeToString(decodedEventList.getBytes()));
                    if (sendMessageResult.getStatusCode() != HttpStatus.CREATED.value()) {
                        throw new UnableToQueueException("Unable to queue due to error: " +
                                sendMessageResult.getStatusCode());
                    }

                    cartReceiptError.setStatus(ReceiptErrorStatusType.REQUEUED);
                } catch (Exception e) {
                    //Error info
                    logger.error("[{}] Error to process cartReceiptError with id {}",
                            context.getFunctionName(), cartReceiptError.getId(), e);
                    cartReceiptError.setMessageError(e.getMessage());
                    cartReceiptError.setStatus(ReceiptErrorStatusType.TO_REVIEW);
                }

                itemsDone.add(cartReceiptError);
            }
        }

        if (!itemsDone.isEmpty()) {
            documentdb.setValue(itemsDone);
        }
    }

    private void updateReceiptToInserted(ExecutionContext context, String cartId) throws CartNotFoundException, UnableToSaveException {
        CartForReceipt receipt = this.cartReceiptCosmosService.getCartForReceipt(cartId);
        receipt.setStatus(CartStatusType.INSERTED);
        logger.debug("[{}] updating cart-receipt with id {} to status {}", context.getFunctionName(), receipt.getId(), receipt.getStatus());
        this.cartReceiptCosmosService.updateCartForReceipt(receipt);
    }
}
