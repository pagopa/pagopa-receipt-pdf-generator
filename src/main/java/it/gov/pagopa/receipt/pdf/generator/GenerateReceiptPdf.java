package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptQueueClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.UnableToQueueException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.service.impl.GenerateReceiptPdfServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.service.impl.ReceiptCosmosServiceImpl;
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
public class GenerateReceiptPdf {

    private final Logger logger = LoggerFactory.getLogger(GenerateReceiptPdf.class);

    private static final int MAX_NUMBER_RETRY = Integer.parseInt(System.getenv().getOrDefault("COSMOS_RECEIPT_QUEUE_MAX_RETRY", "5"));

    private final GenerateReceiptPdfService generateReceiptPdfService;
    private final ReceiptCosmosService receiptCosmosService;
    private final ReceiptQueueClient queueService;

    public GenerateReceiptPdf() {
        this.generateReceiptPdfService = new GenerateReceiptPdfServiceImpl();
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.queueService = ReceiptQueueClientImpl.getInstance();
    }

    GenerateReceiptPdf(
            GenerateReceiptPdfService generateReceiptPdfService,
            ReceiptCosmosService receiptCosmosService,
            ReceiptQueueClient queueService
    ) {
        this.generateReceiptPdfService = generateReceiptPdfService;
        this.receiptCosmosService = receiptCosmosService;
        this.queueService = queueService;
    }

    /**
     * This function will be invoked when a Queue trigger occurs.
     * <p>
     * The received message is mapped from the string to the BizEvent object.
     * Then the receipt's data is retrieved from CosmosDB by the biz-event's id and
     * if the receipt has status INSERTED or RETRY it verify if the debtor's and payer's fiscal code are the same.
     * <p>
     * If different it will generate a pdf for each:
     * <ul>
     *     <li> Complete template for the payer
     *     <li> Partial template for the debtor
     * </ul>
     * If the fiscal code is the same it will generate only one pdf with the complete template
     * <p>
     * For every pdf to generate:
     * <ul>
     *     <li> call the API to the PDF Engine to generate the file from the template
     *     <li> the pdf is saved to the designed Azure Blob Storage
     *     <li> the pdf metadata retrieved from the storage are saved on the receipt's data (file name & url)
     * </ul>
     * If everything succeeded the receipt's status will be updated to GENERATED and saved to CosmosDB
     * <p>
     * The bizEventMessage is re-sent to the queue in case of errors like:
     * <ul>
     *     <li> there is an error generating at least one pdf;
     *     <li> there is an error saving at least one pdf to blob storage;
     *     <li> errors processing the data;
     * </ul>
     * The receipt is discarded in case of:
     * <ul>
     *     <li> the receipt is null
     *     <li> the receipt has not valid event data
     *     <li> the receipt's status is not INSERTED or RETRY
     * </ul>
     * After too many retry the receipt's status will be updated to FAILED
     *
     * @param bizEventMessage BizEventMessage, with biz-event's data, triggering the function
     * @param documentdb      Output binding that will update the receipt data with the pdfs metadata
     * @param context         Function context
     * @throws BizEventNotValidException thrown when an error occur on parsing the message from the queue to a {@link BizEvent}
     * @throws ReceiptNotFoundException  thrown when a receipt associated to the bizEvent is not found on Cosmos DB or the retrieved receipt is null
     */
    @FunctionName("GenerateReceiptProcess")
    public void processGenerateReceipt(
            @QueueTrigger(
                    name = "QueueReceiptWaitingForGen",
                    queueName = "%RECEIPT_QUEUE_TOPIC%",
                    connection = "RECEIPTS_STORAGE_CONN_STRING")
            String bizEventMessage,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    containerName = "receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<Receipt> documentdb,
            final ExecutionContext context
    ) throws BizEventNotValidException, ReceiptNotFoundException, IOException {

        //Map queue bizEventMessage to BizEvent
        List<BizEvent> listOfBizEvent = ReceiptGeneratorUtils.getBizEventListFromMessage(bizEventMessage, context.getFunctionName());

        if (listOfBizEvent.isEmpty()) {
            return;
        }
        String receiptEventReference = ReceiptGeneratorUtils.getReceiptEventReference(listOfBizEvent.get(0));

        logger.info("[{}] function called at {} for receipt with bizEvent reference {}",
                context.getFunctionName(), LocalDateTime.now(), receiptEventReference);

        //Retrieve receipt's data from CosmosDB
        Receipt receipt = this.receiptCosmosService.getReceipt(receiptEventReference);

        //Verify receipt status
        if (isReceiptInInValidState(receipt)) {
            logger.info("[{}] Receipt with id {} is discarded from generation because it is not in INSERTED or RETRY (status: {}) or have null event data (eventData is null: {})",
                    context.getFunctionName(),
                    receipt.getEventId(),
                    receipt.getStatus(),
                    receipt.getEventData() == null);
            return;
        }

        String debtorCF = receipt.getEventData().getDebtorFiscalCode();
        if (debtorCF == null && receipt.getEventData().getPayerFiscalCode() == null) {
            String errorMessage = String.format(
                    "Error processing receipt with id %s : debtor's fiscal code is null",
                    receipt.getEventId()
            );
            receipt.setStatus(ReceiptStatusType.FAILED);
            //Update the receipt's status and error message
            ReasonError reasonError = new ReasonError(HttpStatus.SC_INTERNAL_SERVER_ERROR, errorMessage);
            receipt.setReasonErr(reasonError);
            logger.error("[{}] Error generating PDF: {}", context.getFunctionName(), errorMessage);
            documentdb.setValue(receipt);
            return;
        }

        logger.debug("[{}] Generating pdf for Receipt with id {} and eventId {}",
                context.getFunctionName(),
                receipt.getId(),
                receiptEventReference);
        //Generate and save PDF
        PdfGeneration pdfGeneration;
        Path workingDirPath = ReceiptGeneratorUtils.createWorkingDirectory();
        try {
            pdfGeneration = this.generateReceiptPdfService.generateReceipts(receipt, listOfBizEvent.get(0), workingDirPath);
        } finally {
            ReceiptGeneratorUtils.deleteTempFolder(workingDirPath, logger);
        }

        //Verify PDF generation success
        boolean success;
        try {
            success = this.generateReceiptPdfService.verifyAndUpdateReceipt(receipt, pdfGeneration);
            if (success) {
                receipt.setStatus(ReceiptStatusType.GENERATED);
                receipt.setGenerated_at(System.currentTimeMillis());
                logger.info("[{}] Receipt with id {} being saved with status {}",
                        context.getFunctionName(),
                        receipt.getEventId(),
                        receipt.getStatus());
            } else {
                ReceiptStatusType receiptStatusType;
                //Verify if the max number of retry have been passed
                if (receipt.getNumRetry() > MAX_NUMBER_RETRY) {
                    receiptStatusType = ReceiptStatusType.FAILED;
                } else {
                    receiptStatusType = ReceiptStatusType.RETRY;
                    receipt.setNumRetry(receipt.getNumRetry() + 1);
                    //Send decoded BizEvent to queue
                    Response<SendMessageResult> sendMessageResult =
                            this.queueService.sendMessageToQueue(Base64.getMimeEncoder().encodeToString(bizEventMessage.getBytes()));
                    if (sendMessageResult.getStatusCode() != com.microsoft.azure.functions.HttpStatus.CREATED.value()) {
                        throw new UnableToQueueException("Unable to queue due to error: " +
                                sendMessageResult.getStatusCode());
                    }
                }
                receipt.setStatus(receiptStatusType);
                logger.warn("[{}] Error generating receipt for Receipt {} will be saved with status {}",
                        context.getFunctionName(),
                        receipt.getId(),
                        receiptStatusType);
            }
        } catch (UnableToQueueException | ReceiptGenerationNotToRetryException e) {
            receipt.setStatus(ReceiptStatusType.FAILED);
            logger.error("[{}] PDF Receipt generation for Receipt {} failed. This error will not be retried, the receipt will be saved with status {}",
                    context.getFunctionName(),
                    receipt.getId(),
                    ReceiptStatusType.FAILED, e);
        }
        documentdb.setValue(receipt);
    }

    private boolean isReceiptInInValidState(Receipt receipt) {
        return receipt.getEventData() == null
                || (!receipt.getStatus().equals(ReceiptStatusType.INSERTED) && !receipt.getStatus().equals(ReceiptStatusType.RETRY));
    }
}
