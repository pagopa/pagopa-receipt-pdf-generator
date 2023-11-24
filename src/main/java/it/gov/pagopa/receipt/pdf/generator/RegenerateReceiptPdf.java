package it.gov.pagopa.receipt.pdf.generator;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.generator.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.request.RegenerateReceiptRequest;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.service.impl.GenerateReceiptPdfServiceImpl;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.generator.utils.GenerateReceiptUtils.*;


/**
 * Azure Functions with Azure Http trigger.
 */
public class RegenerateReceiptPdf {



    private final Logger logger = LoggerFactory.getLogger(RegenerateReceiptPdf.class);
    private final BizEventCosmosClient bizEventCosmosClient;
    private final ReceiptCosmosClient receiptCosmosClient;

    private final GenerateReceiptPdfService generateReceiptPdfService;

    public RegenerateReceiptPdf(){
        this.generateReceiptPdfService = new GenerateReceiptPdfServiceImpl();
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
    }

    RegenerateReceiptPdf(BizEventCosmosClient bizEventCosmosClient,
                         ReceiptCosmosClient receiptCosmosClient,
                         GenerateReceiptPdfService generateReceiptPdfService){
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.receiptCosmosClient = receiptCosmosClient;
        this.generateReceiptPdfService = generateReceiptPdfService;
    }


    /**
     * This function will be invoked when a Http Trigger occurs
     *
     * @return response with HttpStatus.OK
     */
    @FunctionName("RegenerateReceiptPdf")
    public HttpResponseMessage run (
            @HttpTrigger(name = "RegenerateReceiptPdfTrigger",
                    methods = {HttpMethod.PUT},
                    route = "regenerateReceiptPdf",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<RegenerateReceiptRequest>> request,
            final ExecutionContext context) {

        try {

            RegenerateReceiptRequest regenerateReceiptRequest = request.getBody().get();

            if (regenerateReceiptRequest.getEventId() != null) {

                BizEvent bizEvent = bizEventCosmosClient.getBizEventDocument(
                        regenerateReceiptRequest.getEventId());

                //Retrieve receipt's data from CosmosDB
                Receipt receipt = getReceipt(context, bizEvent, receiptCosmosClient, logger);

                //Verify receipt status
                if (receipt.getEventData() != null) {

                    logger.info("[{}] Generating pdf for Receipt with id {} and bizEvent with id {}",
                            context.getFunctionName(),
                            receipt.getId(),
                            bizEvent.getId());
                    //Generate and save PDF
                    PdfGeneration pdfGeneration;
                    Path workingDirPath = createWorkingDirectory();
                    try {
                        pdfGeneration = generateReceiptPdfService.generateReceipts(receipt, bizEvent, workingDirPath);

                        //Verify PDF generation success
                        boolean success;
                        success = generateReceiptPdfService.verifyAndUpdateReceipt(receipt, pdfGeneration);

                        return success ?
                                request.createResponseBuilder(HttpStatus.OK)
                                    .body("OK")
                                    .build() :
                                request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).build();

                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                                .build();
                    } finally {
                        deleteTempFolder(workingDirPath, logger);
                    }

                }

            }

            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .build();

        } catch (NoSuchElementException | ReceiptNotFoundException | BizEventNotFoundException exception) {
            logger.error(exception.getMessage(), exception);
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .build();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

}