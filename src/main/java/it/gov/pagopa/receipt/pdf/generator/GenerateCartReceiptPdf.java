package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.receipt.pdf.generator.client.CartQueueClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.CartQueueClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.CartReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToQueueException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfCartGeneration;
import it.gov.pagopa.receipt.pdf.generator.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateCartReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.service.impl.CartReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.service.impl.GenerateCartReceiptPdfServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.utils.ReceiptGeneratorUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class GenerateCartReceiptPdf {

    private final Logger logger = LoggerFactory.getLogger(GenerateCartReceiptPdf.class);

    private static final int MAX_NUMBER_RETRY = Integer.parseInt(System.getenv().getOrDefault("COSMOS_CART_RECEIPT_QUEUE_MAX_RETRY", "5"));

    private final GenerateCartReceiptPdfService generateCartReceiptPdfService;
    private final CartReceiptCosmosService cartReceiptCosmosService;
    private final CartQueueClient cartQueueClient;

    public GenerateCartReceiptPdf() {
        this.generateCartReceiptPdfService = new GenerateCartReceiptPdfServiceImpl();
        this.cartReceiptCosmosService = new CartReceiptCosmosServiceImpl();
        this.cartQueueClient = CartQueueClientImpl.getInstance();
    }

    GenerateCartReceiptPdf(
            GenerateCartReceiptPdfService generateCartReceiptPdfService,
            CartReceiptCosmosService cartReceiptCosmosService,
            CartQueueClient cartQueueClient
    ) {
        this.generateCartReceiptPdfService = generateCartReceiptPdfService;
        this.cartReceiptCosmosService = cartReceiptCosmosService;
        this.cartQueueClient = cartQueueClient;
    }

    /**
     * This function will be invoked when a Queue trigger occurs.
     * <p>
     * The received message is mapped from the string to the list of BizEvent object.
     * Then the cart receipt's data is retrieved from CosmosDB by the biz-event's transaction id and
     * if cart receipt has status INSERTED or RETRY it proceed with PDF generation.
     * <p>
     * It will generate a pdf for each payer and debtor in the cart, if the payer fiscal code is the equal
     * to one of the debtor, it will generate only one pdf this debtor/payer with the complete template
     * <p>
     * For every pdf to generate:
     * <ul>
     *     <li> call the API to the PDF Engine to generate the file from the template
     *     <li> the pdf is saved to the designed Azure Blob Storage
     *     <li> the pdf metadata retrieved from the storage are saved on the receipt's data (file name & url)
     * </ul>
     * If everything succeeded the cart receipt's status will be updated to GENERATED and saved to CosmosDB
     * <p>
     * The bizEventMessage is re-sent to the queue in case of errors like:
     * <ul>
     *     <li> there is an error generating at least one pdf;
     *     <li> there is an error saving at least one pdf to blob storage;
     *     <li> errors processing the data;
     * </ul>
     * The receipt is discarded in case of:
     * <ul>
     *     <li> the cart receipt is null
     *     <li> the cart receipt has not valid payload
     *     <li> the cart receipt's status is not INSERTED or RETRY
     * </ul>
     * After too many retry the cart receipt's status will be updated to FAILED
     *
     * @param bizEventMessage BizEventMessage, with biz-event's data, triggering the function
     * @param documentdb      Output binding that will update the cart receipt data with the pdfs metadata
     * @param context         Function context
     * @throws BizEventNotValidException thrown when an error occur on parsing the message from the queue to a {@link BizEvent}
     */
    @FunctionName("GenerateCartReceiptProcess")
    public void processGenerateCartReceipt(
            @QueueTrigger(
                    name = "QueueCartReceiptWaitingForGen",
                    queueName = "%CART_RECEIPT_QUEUE_TOPIC%",
                    connection = "RECEIPTS_STORAGE_CONN_STRING")
            String bizEventMessage,
            @CosmosDBOutput(
                    name = "CartReceiptDatastore",
                    databaseName = "COSMOS_RECEIPT_DB_NAME",
                    containerName = "CART_FOR_RECEIPT_CONTAINER_NAME",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<CartForReceipt> documentdb,
            final ExecutionContext context
    ) throws BizEventNotValidException, IOException, CartNotFoundException {

        //Map queue bizEventMessage to BizEvent
        List<BizEvent> listOfBizEvent = ReceiptGeneratorUtils.getBizEventListFromMessage(bizEventMessage, context.getFunctionName());

        if (listOfBizEvent.isEmpty()) {
            return;
        }
        String cartReceiptEventReference = ReceiptGeneratorUtils.getCartReceiptEventReference(listOfBizEvent.get(0));

        logger.info("[{}] function called at {} for cart receipt with bizEvent reference {}",
                context.getFunctionName(), LocalDateTime.now(), cartReceiptEventReference);

        //Retrieve cart's data from CosmosDB
        CartForReceipt cart = this.cartReceiptCosmosService.getCartForReceipt(cartReceiptEventReference);

        //Verify cart status
        if (isCartReceiptInInValidState(cart)) {
            logger.info("[{}] Cart receipt with id {} is discarded from generation because it is not in INSERTED " +
                            "or RETRY (status: {}) or have null payload (payload is null: {})",
                    context.getFunctionName(),
                    cart.getEventId(),
                    cart.getStatus(),
                    cart.getPayload() == null);
            return;
        }

        int totalNotice = Integer.parseInt(cart.getPayload().getTotalNotice());
        if (
                totalNotice != listOfBizEvent.size()
                || totalNotice != cart.getPayload().getCart().size()
        ) {
            String errorMessage = String.format(
                    "Error processing cart receipt with id %s : exè",
                    cartReceiptEventReference
            );
            cart.setStatus(CartStatusType.FAILED);
            //Update the cart's status and error message
            ReasonError reasonError = new ReasonError(HttpStatus.SC_INTERNAL_SERVER_ERROR, errorMessage);
            cart.setReasonErr(reasonError);
            logger.error("[{}] Error generating PDF: {}", context.getFunctionName(), errorMessage);
            documentdb.setValue(cart);
            return;
        }

        if (allFiscalCodesAreNull(cart)) {
            String errorMessage = String.format(
                    "Error processing cart receipt with id %s : payer's and debtor's fiscal code are null",
                    cartReceiptEventReference
            );
            cart.setStatus(CartStatusType.FAILED);
            //Update the cart's status and error message
            ReasonError reasonError = new ReasonError(HttpStatus.SC_INTERNAL_SERVER_ERROR, errorMessage);
            cart.setReasonErr(reasonError);
            logger.error("[{}] Error generating PDF: {}", context.getFunctionName(), errorMessage);
            documentdb.setValue(cart);
            return;
        }

        logger.debug("[{}] Generating pdf for Cart receipt with id {} and eventId {}",
                context.getFunctionName(),
                cart.getId(),
                cartReceiptEventReference);
        //Generate and save PDF
        PdfCartGeneration pdfCartGeneration;
        Path workingDirPath = ReceiptGeneratorUtils.createWorkingDirectory();
        try {
            pdfCartGeneration = this.generateCartReceiptPdfService.generateCartReceipts(cart, listOfBizEvent, workingDirPath);
        } finally {
            ReceiptGeneratorUtils.deleteTempFolder(workingDirPath, logger);
        }

        //Verify PDF generation success
        boolean success;
        try {
            success = this.generateCartReceiptPdfService.verifyAndUpdateCartReceipt(cart, pdfCartGeneration);
            if (success) {
                cart.setStatus(CartStatusType.GENERATED);
                cart.setGenerated_at(System.currentTimeMillis());
                logger.debug("[{}] Cart receipt with id {} being saved with status {}",
                        context.getFunctionName(),
                        cart.getEventId(),
                        cart.getStatus());
            } else {
                CartStatusType cartReceiptStatusType;
                //Verify if the max number of retry have been passed
                if (cart.getNumRetry() > MAX_NUMBER_RETRY) {
                    cartReceiptStatusType = CartStatusType.FAILED;
                } else {
                    cartReceiptStatusType = CartStatusType.RETRY;
                    cart.setNumRetry(cart.getNumRetry() + 1);
                    //Send decoded BizEvent to queue
                    Response<SendMessageResult> sendMessageResult =
                            this.cartQueueClient.sendMessageToQueue(Base64.getMimeEncoder().encodeToString(bizEventMessage.getBytes()));
                    if (sendMessageResult.getStatusCode() != com.microsoft.azure.functions.HttpStatus.CREATED.value()) {
                        throw new UnableToQueueException("Unable to queue due to error: " +
                                sendMessageResult.getStatusCode());
                    }
                }
                cart.setStatus(cartReceiptStatusType);
                logger.error("[{}] Error generating cart for Receipt {} will be saved with status {}",
                        context.getFunctionName(),
                        cart.getId(),
                        cartReceiptStatusType);
            }
        } catch (UnableToQueueException | CartReceiptGenerationNotToRetryException e) {
            cart.setStatus(CartStatusType.FAILED);
            logger.error("[{}] PDF Receipt generation for Cart receipt {} failed. This error will not be retried, the cart will be saved with status {}",
                    context.getFunctionName(),
                    cart.getId(),
                    ReceiptStatusType.FAILED, e);
        }
        documentdb.setValue(cart);
    }

    private boolean allFiscalCodesAreNull(CartForReceipt cart) {
        boolean allDebtorFiscalCodeAreNull = cart.getPayload().getCart().stream()
                .allMatch(cartPayment -> cartPayment.getDebtorFiscalCode() == null);

        return allDebtorFiscalCodeAreNull && cart.getPayload().getPayerFiscalCode() == null;
    }

    private boolean isCartReceiptInInValidState(CartForReceipt cartForReceipt) {
        return cartForReceipt.getPayload() == null
                || (!cartForReceipt.getStatus().equals(CartStatusType.INSERTED) && !cartForReceipt.getStatus().equals(CartStatusType.RETRY));
    }
}
