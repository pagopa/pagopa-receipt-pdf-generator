package it.gov.pagopa.receipt.pdf.generator;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import it.gov.pagopa.receipt.pdf.generator.utils.ReceiptUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;

import java.util.logging.Level;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
import java.util.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class GenerateReceiptPdf {

    private Logger logger = Logger.getLogger(GenerateReceiptPdf.class.getName());
    

    private static final int MAX_NUMBER_RETRY = Integer.parseInt(System.getenv().getOrDefault("COSMOS_RECEIPT_QUEUE_MAX_RETRY", "5"));
    private static final String WORKING_DIRECTORY_PATH = System.getenv().getOrDefault("WORKING_DIRECTORY_PATH", "/temp");

    private static final String PATTERN_FORMAT = "yyyy.MM.dd.HH.mm.ss";

    private final GenerateReceiptPdfService generateReceiptPdfService;
    private final ReceiptCosmosService receiptCosmosService;
    private final ReceiptQueueClient queueService;

    public GenerateReceiptPdf() {
        this.generateReceiptPdfService = new GenerateReceiptPdfServiceImpl();
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.queueService = ReceiptQueueClientImpl.getInstance();
    }

    GenerateReceiptPdf(GenerateReceiptPdfService generateReceiptPdfService, ReceiptCosmosService receiptCosmosService, ReceiptQueueClient queueService) {
        this.generateReceiptPdfService = generateReceiptPdfService;
        this.receiptCosmosService = receiptCosmosService;
        this.queueService = queueService;
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
                    containerName = "receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<Receipt> documentdb,
            final ExecutionContext context) throws BizEventNotValidException, ReceiptNotFoundException, IOException {

        logger = context.getLogger();
        
        logger.info(String.format("[%s] function called at %s", context.getFunctionName(), LocalDateTime.now()));

        //Map queue bizEventMessage to BizEvent
        List<BizEvent> listOfBizEvent = getBizEventListFromMessage(context, bizEventMessage);

        if(!listOfBizEvent.isEmpty()){
            String receiptEventReference = ReceiptUtils.getReceiptEventReference(listOfBizEvent.get(0), listOfBizEvent.size() > 1);

            logger.info(() -> String.format("[%s] function called at %s for receipt with bizEvent reference %s", context.getFunctionName(), LocalDateTime.now(), receiptEventReference));

            //Retrieve receipt's data from CosmosDB
            Receipt receipt = this.receiptCosmosService.getReceipt(receiptEventReference);

            //Verify receipt status
            if (isReceiptInInValidState(receipt)) {
                logger.info(() -> String.format("[%s] Receipt with id %s is discarded from generation because it is not in INSERTED or RETRY (status: %s) or have null event data (eventData is null: %s)",
                        context.getFunctionName(),
                        receipt.getEventId(),
                        receipt.getStatus(),
                        receipt.getEventData() == null));
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
                logger.severe(String.format("[%s] Error generating PDF: %s", context.getFunctionName(), errorMessage));
                documentdb.setValue(receipt);
                return;
            }

            logger.info(String.format("[%s] Generating pdf for Receipt with id %s and eventId %s",
                    context.getFunctionName(),
                    receipt.getId(),
                    receiptEventReference));
            //Generate and save PDF
            PdfGeneration pdfGeneration;
            Path workingDirPath = createWorkingDirectory();
            logger.info(String.format("[%s] Generating pdf for Receipt with id %s and eventId %s",
                    context.getFunctionName(),
                    receipt.getId(),
                    "Ho creato la dir ......."));
            try {
                pdfGeneration = generateReceiptPdfService.generateReceipts(receipt, listOfBizEvent, workingDirPath);
            } finally {
                deleteTempFolder(workingDirPath);
            }

            //Verify PDF generation success
            boolean success;
            try {
                success = generateReceiptPdfService.verifyAndUpdateReceipt(receipt, pdfGeneration);
                if (success) {
                    receipt.setStatus(ReceiptStatusType.GENERATED);
                    receipt.setGenerated_at(System.currentTimeMillis());
                    logger.fine(() -> String.format("[%s] Receipt with id %s being saved with status %s",
                            context.getFunctionName(),
                            receipt.getEventId(),
                            receipt.getStatus()));
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
                    logger.fine(() -> String.format("[%s] Error generating receipt for Receipt %s will be saved with status %s",
                            context.getFunctionName(),
                            receipt.getId(),
                            receiptStatusType));
                }
            } catch (UnableToQueueException | ReceiptGenerationNotToRetryException e) {
                receipt.setStatus(ReceiptStatusType.FAILED);
                logger.severe(() -> String.format("[%s] PDF Receipt generation for Receipt %s failed. This error will not be retried, the receipt will be saved with status %s",
                        context.getFunctionName(),
                        receipt.getId(),
                        ReceiptStatusType.FAILED, e));
            }
            documentdb.setValue(receipt);
        }
    }

    private boolean isReceiptInInValidState(Receipt receipt) {
        return receipt.getEventData() == null
                || (!receipt.getStatus().equals(ReceiptStatusType.INSERTED) && !receipt.getStatus().equals(ReceiptStatusType.RETRY));
    }

    private List<BizEvent> getBizEventListFromMessage(ExecutionContext context, String bizEventMessage) throws BizEventNotValidException {
        try {
            return ObjectMapperUtils.mapBizEventListString(bizEventMessage, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            String errorMsg = String.format("[%s] Error parsing the message coming from the queue",
                    context.getFunctionName());
            throw new BizEventNotValidException(errorMsg, e);
        }
    }

    private Path createWorkingDirectory() throws IOException {
    	Path p = null;
    	try {
        File workingDirectory = new File(WORKING_DIRECTORY_PATH);
        if (!workingDirectory.exists()) {
            try {
                Files.createDirectory(workingDirectory.toPath());
            } catch (FileAlreadyExistsException ignored) {
                // If the directory already exists, we can ignore this exception
				logger.severe(() -> String.format("Working directory already exists: %s", WORKING_DIRECTORY_PATH));
            }
            p = Files.createTempDirectory(workingDirectory.toPath(),
                    DateTimeFormatter.ofPattern(PATTERN_FORMAT)
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now()));
        }
    	} catch (IOException e) {
			logger.severe(() -> String.format("Unable to create working directory: %s", WORKING_DIRECTORY_PATH, e));
			throw e;
		}
        
        return p;
    }

    private void deleteTempFolder(Path workingDirPath) {
        try {
            FileUtils.deleteDirectory(workingDirPath.toFile());
        } catch (IOException e) {
        	logger.log(Level.WARNING, String.format("Unable to clear working directory: %s", workingDirPath), e);
        }
    }
}
