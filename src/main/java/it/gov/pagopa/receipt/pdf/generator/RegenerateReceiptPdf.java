package it.gov.pagopa.receipt.pdf.generator;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import io.micrometer.core.instrument.util.StringUtils;
import it.gov.pagopa.receipt.pdf.generator.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.helpdesk.ProblemJson;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.service.helpdesk.HelpdeskService;
import it.gov.pagopa.receipt.pdf.generator.service.helpdesk.impl.HelpdeskServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.service.impl.GenerateReceiptPdfServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.service.impl.ReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils;
import it.gov.pagopa.receipt.pdf.generator.utils.ReceiptGeneratorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils.isBizEventInvalid;
import static it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils.isReceiptStatusValid;


/**
 * Azure Functions with Azure Http trigger.
 */
public class RegenerateReceiptPdf {

    private static final Logger logger = LoggerFactory.getLogger(RegenerateReceiptPdf.class);

    public static final String LOG_FORMAT = "[{}] {}";

    private final BizEventCosmosClient bizEventCosmosClient;
    private final ReceiptCosmosService receiptCosmosService;
    private final HelpdeskService helpdeskService;
    private final GenerateReceiptPdfService generateReceiptPdfService;

    public RegenerateReceiptPdf() {
        this.helpdeskService = new HelpdeskServiceImpl();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.generateReceiptPdfService = new GenerateReceiptPdfServiceImpl();
    }

    RegenerateReceiptPdf(
            BizEventCosmosClient bizEventCosmosClient,
            ReceiptCosmosService receiptCosmosService, HelpdeskService helpdeskService,
            GenerateReceiptPdfService generateReceiptPdfService
    ) {
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.receiptCosmosService = receiptCosmosService;
        this.helpdeskService = helpdeskService;
        this.generateReceiptPdfService = generateReceiptPdfService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs
     *
     * @return response with HttpStatus.OK
     */
    @FunctionName("RegenerateReceiptFunc")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RegenerateReceiptPdfFuncTrigger",
                    methods = {HttpMethod.POST},
                    route = "receipts/{biz-event-id}/regenerate-receipt-pdf",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("biz-event-id") String bizEventId,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    containerName = "receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<Receipt> documentdb,
            final ExecutionContext context
    ) throws IOException {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (StringUtils.isBlank(bizEventId)) {
            String errMsg = "Missing valid eventId parameter";
            logger.error(LOG_FORMAT, context.getFunctionName(), errMsg);
            return buildErrorResponse(request, HttpStatus.BAD_REQUEST, errMsg);
        }

        BizEvent bizEvent;
        try {
            bizEvent = this.bizEventCosmosClient.getBizEventDocument(bizEventId);
        } catch (BizEventNotFoundException exception) {
            String errMsg = String.format("BizEvent not found with id %s", bizEventId);
            logger.error(LOG_FORMAT, context.getFunctionName(), errMsg, exception);
            return buildErrorResponse(request, HttpStatus.BAD_REQUEST, errMsg);
        }

        HelpdeskUtils.BizEventValidityCheck bizEventValidityCheck = isBizEventInvalid(bizEvent);
        if (bizEventValidityCheck.invalid()) {
            return buildErrorResponse(request, HttpStatus.BAD_REQUEST, bizEventValidityCheck.error());
        }

        Integer totalNotice = HelpdeskUtils.getTotalNotice(bizEvent, context, logger);
        if (totalNotice > 1) {
            // TODO cart management: future developments to be defined
            return buildErrorResponse(
                    request,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to regenerate receipt, the provided biz event is part of a cart"
            );
        }

        Receipt receipt = bulidNewReceipt(context, bizEvent);
        if (!isReceiptStatusValid(receipt)) {
            String errDetail = String.format(
                    "Failed to re-create receipt entity with eventId %s: %s",
                    receipt.getEventId(),
                    receipt.getReasonErr() != null ? receipt.getReasonErr().getMessage() : ""
            );
            logger.error(LOG_FORMAT, context.getFunctionName(), errDetail);
            return buildErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, errDetail);
        }

        PdfGeneration pdfGeneration = generatePDFReceipt(receipt, bizEvent);
        try {
            boolean success = this.generateReceiptPdfService.verifyAndUpdateReceipt(receipt, pdfGeneration);
            if (success) {
                receipt.setInserted_at(System.currentTimeMillis());
                receipt.setGenerated_at(System.currentTimeMillis());
                receipt.setStatus(ReceiptStatusType.IO_NOTIFIED);
            } else {
                return buildErrorResponse(
                        request,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Receipt could not be updated with the new attachments"
                );
            }
        } catch (ReceiptGenerationNotToRetryException e) {
            logger.error("[{}] Not retryable error occurred while generating the receipt with event id {}",
                    context.getFunctionName(), receipt.getEventId(), e);
            return buildErrorResponse(
                    request,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error during receipt generation: " + e.getMessage()
            );
        }

        // updated the receipt on cosmos
        documentdb.setValue(receipt);

        return request.createResponseBuilder(HttpStatus.OK)
                .body("Regenerate receipt completed successfully")
                .build();
    }

    private PdfGeneration generatePDFReceipt(Receipt receipt, BizEvent bizEvent) throws IOException {
        Path workingDirPath = ReceiptGeneratorUtils.createWorkingDirectory();
        try {
            return this.generateReceiptPdfService.generateReceipts(receipt, bizEvent, workingDirPath);
        } finally {
            ReceiptGeneratorUtils.deleteTempFolder(workingDirPath, logger);
        }
    }

    private Receipt bulidNewReceipt(ExecutionContext context, BizEvent bizEvent) {
        Receipt receipt = this.helpdeskService.createReceipt(bizEvent);
        try {
            Receipt existingReceipt = this.receiptCosmosService.getReceipt(bizEvent.getId());

            // keep notification info if present to avoid data loss on regeneration
            receipt.setId(existingReceipt.getId());
            receipt.setIoMessageData(existingReceipt.getIoMessageData());
            receipt.setNotified_at(existingReceipt.getNotified_at());
        } catch (ReceiptNotFoundException e) {
            logger.info("[{}] Receipt not found with the provided biz event id, a new receipt will be generated",
                    context.getFunctionName());
        }
        return receipt;
    }

    private HttpResponseMessage buildErrorResponse(
            HttpRequestMessage<Optional<String>> request,
            HttpStatus httpStatus,
            String errMsg
    ) {
        return request
                .createResponseBuilder(httpStatus)
                .body(ProblemJson.builder()
                        .title(httpStatus.name())
                        .detail(errMsg)
                        .status(httpStatus.value())
                        .build())
                .build();
    }
}