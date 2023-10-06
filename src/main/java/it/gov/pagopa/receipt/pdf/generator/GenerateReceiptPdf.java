package it.gov.pagopa.receipt.pdf.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueOutput;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.service.impl.GenerateReceiptPdfServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class GenerateReceiptPdf {

    private final Logger logger = LoggerFactory.getLogger(GenerateReceiptPdf.class);

    private static final int MAX_NUMBER_RETRY = Integer.parseInt(System.getenv().getOrDefault("COSMOS_RECEIPT_QUEUE_MAX_RETRY", "5"));

    private final GenerateReceiptPdfService generateReceiptPdfService;
    private final ReceiptCosmosClient receiptCosmosClient;

    public GenerateReceiptPdf() {
        this.generateReceiptPdfService = new GenerateReceiptPdfServiceImpl();
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
    }

    GenerateReceiptPdf(GenerateReceiptPdfService generateReceiptPdfService, ReceiptCosmosClient receiptCosmosClient) {
        this.generateReceiptPdfService = generateReceiptPdfService;
        this.receiptCosmosClient = receiptCosmosClient;
    }

    /**
     * This function will be invoked when a Queue trigger occurs
     * #
     * The biz-event is mapped from the string to the BizEvent object
     * The receipt's data is retrieved from CosmosDB by the biz-event's id
     * If receipt has status INSERTED or RETRY
     * Is verified if the debtor's and payer's fiscal code are the same
     * If different it will generate a pdf for each:
     * - Complete template for the payer
     * - Partial template for the debtor
     * If the fiscal code is the same it will generate only one pdf with the complete template
     * For every pdf to generate:
     * - call the API to the PDF Engine to generate the file from the template
     * - the pdf is saved to the designed Azure Blob Storage
     * - the pdf metadata retrieved from the storage are saved on the receipt's data (file name & url)
     * If everything succeeded the receipt's status will be updated to GENERATED and saved to CosmosDB
     * #
     * The bizEventMessage is re-sent to the queue in case of errors like:
     * - there is an error generating at least one pdf;
     * - there is an error saving at least one pdf to blob storage;
     * - errors processing the data;
     * #
     * The receipt is discarded in case of:
     * - the receipt is null
     * - the receipt has not valid event data
     * - the receipt's status is not INSERTED or RETRY
     * #
     * After too many retry the receipt's status will be updated to FAILED
     *
     * @param bizEventMessage BizEventMessage, with biz-event's data, triggering the function
     * @param documentdb      Output binding that will update the receipt data with the pdfs metadata
     * @param requeueMessage  Output binding that will re-send the bizEventMessage to the queue in case of errors
     * @param context         Function context
     * @throws BizEventNotValidException thrown when an error occur on parsing the message from the queue to a {@link BizEvent}
     * @throws ReceiptNotFoundException thrown when a receipt associated to the bizEvent is not found on Cosmos DB or the retrieved receipt is null
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
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<Receipt> documentdb,
            @QueueOutput(
                    name = "QueueReceiptWaitingForGenOutput",
                    queueName = "%RECEIPT_QUEUE_TOPIC%",
                    connection = "RECEIPTS_STORAGE_CONN_STRING")
            OutputBinding<String> requeueMessage,
            final ExecutionContext context) throws BizEventNotValidException, ReceiptNotFoundException {

        //Map queue bizEventMessage to BizEvent
        BizEvent bizEvent = getBizEventFromMessage(context, bizEventMessage);

        logger.info("[{}] function called at {} for bizEvent with id {}",
                context.getFunctionName(), LocalDateTime.now(), bizEvent.getId());

        //Retrieve receipt's data from CosmosDB
        Receipt receipt = getReceipt(context, bizEvent);

        //Verify receipt status
        if (isReceiptInInValidState(receipt)) {
            logger.info("[{}] Receipt with id {} not in INSERTED or RETRY (status: {}) or have null event data (eventData is null: {})",
                    context.getFunctionName(),
                    receipt.getEventId(),
                    receipt.getStatus(),
                    receipt.getEventData() == null);
            return;
        }
        //Verify if debtor's and payer's fiscal code are the same
        String debtorCF = receipt.getEventData().getDebtorFiscalCode();

        if (debtorCF != null) {
            logger.info("[{}] Generating pdf for Receipt with id {} and bizEvent with id {}",
                    context.getFunctionName(),
                    receipt.getId(),
                    bizEvent.getId());

            //Generate and save PDF
            PdfGeneration pdfGeneration = generateReceiptPdfService.generateReceipts(receipt, bizEvent);

            //Verify PDF generation success
            boolean success = generateReceiptPdfService.verifyAndUpdateReceipt(receipt, pdfGeneration);
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
                    requeueMessage.setValue(bizEventMessage);
                }
                receipt.setStatus(receiptStatusType);
                logger.error("[{}] Error generating receipt for Receipt {} will be saved with status {}",
                        context.getFunctionName(),
                        receipt.getId(),
                        receiptStatusType);
            }
        } else {
            String errorMessage = String.format(
                    "Error processing receipt with id %s : debtor's fiscal code is null",
                    receipt.getEventId()
            );
            receipt.setStatus(ReceiptStatusType.FAILED);
            //Update the receipt's status and error message
            ReasonError reasonError = new ReasonError(HttpStatus.SC_INTERNAL_SERVER_ERROR, errorMessage);
            receipt.setReasonErr(reasonError);
            logger.error("[{}] Error generating PDF: {}", context.getFunctionName(), errorMessage);
        }

        documentdb.setValue(receipt);
    }

    private boolean isReceiptInInValidState(Receipt receipt) {
        return receipt.getEventData() == null
                || (!receipt.getStatus().equals(ReceiptStatusType.INSERTED) && !receipt.getStatus().equals(ReceiptStatusType.RETRY));
    }

    private Receipt getReceipt(ExecutionContext context, BizEvent bizEvent) throws ReceiptNotFoundException {
        Receipt receipt;
        //Retrieve receipt from CosmosDB
        try {
            receipt = receiptCosmosClient.getReceiptDocument(bizEvent.getId());
        } catch (ReceiptNotFoundException e) {
            String errorMsg = String.format("[%s] Receipt not found with the biz-event id %s",
                    context.getFunctionName(), bizEvent.getId());
            throw new ReceiptNotFoundException(errorMsg, e);
        }

        if (receipt == null) {
            String errorMsg = "[{}] Receipt retrieved with the biz-event id {} is null";
            logger.debug(errorMsg, context.getFunctionName(), bizEvent.getId());
            throw new ReceiptNotFoundException(errorMsg);
        }
        return receipt;
    }

    private BizEvent getBizEventFromMessage(ExecutionContext context, String bizEventMessage) throws BizEventNotValidException {
        try {
            return ObjectMapperUtils.mapString(bizEventMessage, BizEvent.class);
        } catch (JsonProcessingException e) {
            String errorMsg = String.format("[%s] Error parsing the message coming from the queue",
                    context.getFunctionName());
            throw new BizEventNotValidException(errorMsg, e);
        }
    }
}
