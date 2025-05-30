package it.gov.pagopa.receipt.pdf.generator.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.generator.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptBlobClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptBlobClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.exception.GeneratePDFException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.PDFReceiptGenerationException;
import it.gov.pagopa.receipt.pdf.generator.exception.SavePDFToBlobException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.generator.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.generator.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.model.template.ReceiptPDFTemplate;
import it.gov.pagopa.receipt.pdf.generator.service.BuildTemplateService;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import lombok.Setter;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class GenerateReceiptPdfServiceImpl implements GenerateReceiptPdfService {

    private final Logger logger = LoggerFactory.getLogger(GenerateReceiptPdfServiceImpl.class);

    private static final String TEMPLATE_PREFIX = "pagopa-ricevuta";
    private static final String PAYER_TEMPLATE_SUFFIX = "p";
    private static final String DEBTOR_TEMPLATE_SUFFIX = "d";

    public static final int ALREADY_CREATED = 208;

    private final PdfEngineClient pdfEngineClient;
    private final ReceiptBlobClient receiptBlobClient;
    private final BuildTemplateService buildTemplateService;
    @Setter
    private long minFileLength = Long.parseLong(
            System.getenv().getOrDefault("MIN_PDF_LENGTH", "10000"));

    public GenerateReceiptPdfServiceImpl() {
        this.pdfEngineClient = PdfEngineClientImpl.getInstance();
        this.receiptBlobClient = ReceiptBlobClientImpl.getInstance();
        this.buildTemplateService = new BuildTemplateServiceImpl();
    }

    GenerateReceiptPdfServiceImpl(PdfEngineClient pdfEngineClient, ReceiptBlobClient receiptBlobClient, BuildTemplateService buildTemplateService) {
        this.pdfEngineClient = pdfEngineClient;
        this.receiptBlobClient = receiptBlobClient;
        this.buildTemplateService = buildTemplateService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PdfGeneration generateReceipts(Receipt receipt, List<BizEvent> listOfBizEvents, Path workingDirPath) {

        logger.info("sono entrato in generateReceipts");

        PdfGeneration pdfGeneration = new PdfGeneration();

        String debtorCF = receipt.getEventData().getDebtorFiscalCode();
        String payerCF = receipt.getEventData().getPayerFiscalCode();

        if (payerCF != null) {

            if (payerCF.equals(debtorCF)) {
                pdfGeneration.setGenerateOnlyDebtor(true);
                //Generate debtor's complete PDF
                if (receiptAlreadyCreated(receipt.getMdAttach())) {
                    pdfGeneration.setDebtorMetadata(PdfMetadata.builder().statusCode(ALREADY_CREATED).build());
                    return pdfGeneration;
                }
                PdfMetadata generationResult = generateAndSavePDFReceipt(listOfBizEvents, receipt, PAYER_TEMPLATE_SUFFIX, true, workingDirPath);
                pdfGeneration.setDebtorMetadata(generationResult);
                return pdfGeneration;
            }

            //Generate payer's complete PDF
            if (receiptAlreadyCreated(receipt.getMdAttachPayer())) {
                pdfGeneration.setPayerMetadata(PdfMetadata.builder().statusCode(ALREADY_CREATED).build());
            } else {

                PdfMetadata generationResult = generateAndSavePDFReceipt(listOfBizEvents, receipt, PAYER_TEMPLATE_SUFFIX, false, workingDirPath);
                pdfGeneration.setPayerMetadata(generationResult);
            }
        } else {
            pdfGeneration.setGenerateOnlyDebtor(true);
        }

        //Generate debtor's partial PDF
        if (receiptAlreadyCreated(receipt.getMdAttach())) {
            pdfGeneration.setDebtorMetadata(PdfMetadata.builder().statusCode(ALREADY_CREATED).build());
        } else if (!"ANONIMO".equals(debtorCF)) {
            PdfMetadata generationResult = generateAndSavePDFReceipt(listOfBizEvents, receipt, DEBTOR_TEMPLATE_SUFFIX, true, workingDirPath);
            pdfGeneration.setDebtorMetadata(generationResult);
        }

        return pdfGeneration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifyAndUpdateReceipt(Receipt receipt, PdfGeneration pdfGeneration) throws ReceiptGenerationNotToRetryException {
        PdfMetadata debtorMetadata = pdfGeneration.getDebtorMetadata();
        boolean result = true;

        if (receipt.getEventData() != null && !"ANONIMO".equals(receipt.getEventData().getDebtorFiscalCode())) {

            if (debtorMetadata == null) {
                logger.error("Unexpected result for debtor pdf receipt generation. Receipt id {}", receipt.getId());
                return false;
            }

            if (debtorMetadata.getStatusCode() == HttpStatus.SC_OK) {
                ReceiptMetadata receiptMetadata = new ReceiptMetadata();
                receiptMetadata.setName(debtorMetadata.getDocumentName());
                receiptMetadata.setUrl(debtorMetadata.getDocumentUrl());

                receipt.setMdAttach(receiptMetadata);
            } else if (debtorMetadata.getStatusCode() != ALREADY_CREATED) {
                ReasonError reasonError = new ReasonError(debtorMetadata.getStatusCode(), debtorMetadata.getErrorMessage());
                receipt.setReasonErr(reasonError);
                result = false;
            }
        }

        if (pdfGeneration.isGenerateOnlyDebtor()) {
            if (debtorMetadata.getStatusCode() == ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode()) {
                String errMsg = String.format("Debtor receipt generation fail with status %s", debtorMetadata.getStatusCode());
                throw new ReceiptGenerationNotToRetryException(errMsg);
            }
            return result;
        }

        PdfMetadata payerMetadata = pdfGeneration.getPayerMetadata();
        if (payerMetadata == null) {
            logger.error("Unexpected result for payer pdf receipt generation. Receipt id {}", receipt.getId());
            return false;
        }

        if (payerMetadata.getStatusCode() == HttpStatus.SC_OK) {
            ReceiptMetadata receiptMetadata = new ReceiptMetadata();
            receiptMetadata.setName(payerMetadata.getDocumentName());
            receiptMetadata.setUrl(payerMetadata.getDocumentUrl());

            receipt.setMdAttachPayer(receiptMetadata);
        } else if (payerMetadata.getStatusCode() != ALREADY_CREATED) {
            ReasonError reasonErrorPayer = new ReasonError(payerMetadata.getStatusCode(), payerMetadata.getErrorMessage());
            receipt.setReasonErrPayer(reasonErrorPayer);
            result = false;
        }

        if ((debtorMetadata != null && debtorMetadata.getStatusCode() == ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode())
                || payerMetadata.getStatusCode() == ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode()) {
            String errMsg = String.format("Receipt generation fail for debtor (status: %s) and/or payer (status: %s)",
                   debtorMetadata != null ? debtorMetadata.getStatusCode() : "N/A", payerMetadata.getStatusCode());
            throw new ReceiptGenerationNotToRetryException(errMsg);
        }
        return result;
    }

    private PdfMetadata generateAndSavePDFReceipt(List<BizEvent> listOfBizEvents, Receipt receipt, String templateSuffix, boolean isGeneratingDebtor, Path workingDirPath) {
        logger.info("sono entrato in generateAndSavePDFReceipt");
        try {
            logger.info("sono entrato in generateAndSavePDFReceipt-1");
            ReceiptPDFTemplate template = buildTemplateService.buildTemplate(listOfBizEvents, isGeneratingDebtor, receipt);
            logger.info("sono entrato in generateAndSavePDFReceipt-2");
            String dateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
            logger.info("sono entrato in generateAndSavePDFReceipt-3");
            String blobName = String.format("%s-%s-%s-%s", TEMPLATE_PREFIX, dateFormatted, receipt.getEventId(), templateSuffix);
            logger.info("sono entrato in generateAndSavePDFReceipt-4");
            PdfEngineResponse pdfEngineResponse = generatePDFReceipt(template, workingDirPath);
            logger.info("sono entrato in generateAndSavePDFReceipt-5");
            return saveToBlobStorage(pdfEngineResponse, blobName);
            logger.info("sono entrato in generateAndSavePDFReceipt-6");
        } catch (PDFReceiptGenerationException e) {
            logger.error("An error occurred when generating or saving the PDF receipt with eventId {}. Error: {}", receipt.getEventId(), e.getMessage(), e);
            return PdfMetadata.builder().statusCode(e.getStatusCode()).errorMessage(e.getMessage()).build();
        }
    }

    private PdfMetadata saveToBlobStorage(PdfEngineResponse pdfEngineResponse, String blobName) throws SavePDFToBlobException {
        String tempPdfPath = pdfEngineResponse.getTempPdfPath();

        if (new File(tempPdfPath).length() < minFileLength) {
            throw new SavePDFToBlobException("Minimum file size not reached", ReasonErrorCode.ERROR_BLOB_STORAGE.getCode());
        }

        BlobStorageResponse blobStorageResponse;
        //Save to Blob Storage
        try (BufferedInputStream pdfStream = new BufferedInputStream(new FileInputStream(tempPdfPath))) {
            blobStorageResponse = receiptBlobClient.savePdfToBlobStorage(pdfStream, blobName);
        } catch (Exception e) {
            throw new SavePDFToBlobException("Error saving pdf to blob storage", ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), e);
        }

        if (blobStorageResponse.getStatusCode() != com.microsoft.azure.functions.HttpStatus.CREATED.value()) {
            String errMsg = String.format("Error saving pdf to blob storage, storage responded with status %s",
                    blobStorageResponse.getStatusCode());
            throw new SavePDFToBlobException(errMsg, ReasonErrorCode.ERROR_BLOB_STORAGE.getCode());
        }

        //Update PDF metadata
        return PdfMetadata.builder()
                .documentName(blobStorageResponse.getDocumentName())
                .documentUrl(blobStorageResponse.getDocumentUrl())
                .statusCode(HttpStatus.SC_OK)
                .build();
    }

    private PdfEngineResponse generatePDFReceipt(ReceiptPDFTemplate template, Path workingDirPath) throws PDFReceiptGenerationException {
        PdfEngineRequest request = new PdfEngineRequest();

        URL templateStream = GenerateReceiptPdfServiceImpl.class.getClassLoader().getResource("template.zip");
        //Build the request
        request.setTemplate(templateStream);
        request.setData(parseTemplateDataToString(template));
        request.setApplySignature(false);

        PdfEngineResponse pdfEngineResponse = pdfEngineClient.generatePDF(request, workingDirPath);

        if (pdfEngineResponse.getStatusCode() != HttpStatus.SC_OK) {
            String errMsg = String.format("PDF-Engine response KO (%s): %s", pdfEngineResponse.getStatusCode(), pdfEngineResponse.getErrorMessage());
            throw new GeneratePDFException(errMsg, pdfEngineResponse.getStatusCode());
        }

        return pdfEngineResponse;
    }

    private String parseTemplateDataToString(ReceiptPDFTemplate template) throws GeneratePDFException {
        try {
            return ObjectMapperUtils.writeValueAsString(template);
        } catch (JsonProcessingException e) {
            throw new GeneratePDFException("Error preparing input data for receipt PDF template", ReasonErrorCode.ERROR_PDF_ENGINE.getCode(), e);
        }
    }

    private boolean receiptAlreadyCreated(ReceiptMetadata receiptMetadata) {
        return receiptMetadata != null
                && receiptMetadata.getUrl() != null
                && receiptMetadata.getName() != null
                && !receiptMetadata.getUrl().isEmpty()
                && !receiptMetadata.getName().isEmpty();
    }
}
