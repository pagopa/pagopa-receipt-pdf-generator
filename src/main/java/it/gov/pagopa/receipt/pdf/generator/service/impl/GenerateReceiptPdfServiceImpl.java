package it.gov.pagopa.receipt.pdf.generator.service.impl;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.exception.PDFReceiptGenerationException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.model.template.ReceiptPDFTemplate;
import it.gov.pagopa.receipt.pdf.generator.service.BuildTemplateService;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.service.PdfEngineService;
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptBlobStorageService;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.ALREADY_CREATED;
import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.DEBTOR_TEMPLATE_SUFFIX;
import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.PAYER_TEMPLATE_SUFFIX;
import static it.gov.pagopa.receipt.pdf.generator.utils.ReceiptGeneratorUtils.receiptAlreadyCreated;

public class GenerateReceiptPdfServiceImpl implements GenerateReceiptPdfService {

    private final Logger logger = LoggerFactory.getLogger(GenerateReceiptPdfServiceImpl.class);

    private static final String TEMPLATE_PREFIX = "pagopa-ricevuta";

    private final PdfEngineService pdfEngineService;
    private final ReceiptBlobStorageService receiptBlobStorageService;
    private final BuildTemplateService buildTemplateService;

    public GenerateReceiptPdfServiceImpl() {
        this.pdfEngineService = new PdfEngineServiceImpl();
        this.receiptBlobStorageService = new ReceiptBlobStorageServiceImpl();
        this.buildTemplateService = new BuildTemplateServiceImpl();
    }

    GenerateReceiptPdfServiceImpl(
            PdfEngineService pdfEngineService,
            ReceiptBlobStorageService receiptBlobStorageService,
            BuildTemplateService buildTemplateService
    ) {
        this.pdfEngineService = pdfEngineService;
        this.receiptBlobStorageService = receiptBlobStorageService;
        this.buildTemplateService = buildTemplateService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PdfGeneration generateReceipts(Receipt receipt, BizEvent bizEvent, Path workingDirPath) {
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
                PdfMetadata generationResult = generateAndSavePDFReceipt(bizEvent, receipt, PAYER_TEMPLATE_SUFFIX, false, workingDirPath);
                pdfGeneration.setDebtorMetadata(generationResult);
                return pdfGeneration;
            }

            //Generate payer's complete PDF
            if (receiptAlreadyCreated(receipt.getMdAttachPayer())) {
                pdfGeneration.setPayerMetadata(PdfMetadata.builder().statusCode(ALREADY_CREATED).build());
            } else {

                PdfMetadata generationResult = generateAndSavePDFReceipt(bizEvent, receipt, PAYER_TEMPLATE_SUFFIX, false, workingDirPath);
                pdfGeneration.setPayerMetadata(generationResult);
            }
        } else {
            pdfGeneration.setGenerateOnlyDebtor(true);
        }

        //Generate debtor's partial PDF
        if (receiptAlreadyCreated(receipt.getMdAttach())) {
            pdfGeneration.setDebtorMetadata(PdfMetadata.builder().statusCode(ALREADY_CREATED).build());
        } else if (!"ANONIMO".equals(debtorCF)) {
            PdfMetadata generationResult = generateAndSavePDFReceipt(bizEvent, receipt, DEBTOR_TEMPLATE_SUFFIX, true, workingDirPath);
            pdfGeneration.setDebtorMetadata(generationResult);
        }

        return pdfGeneration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifyAndUpdateReceipt(
            Receipt receipt,
            PdfGeneration pdfGeneration
    ) throws ReceiptGenerationNotToRetryException {
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

    private PdfMetadata generateAndSavePDFReceipt(
            BizEvent bizEvent,
            Receipt receipt,
            String templateSuffix,
            boolean isGeneratingDebtor,
            Path workingDirPath
    ) {
        try {
            ReceiptPDFTemplate template = this.buildTemplateService.buildTemplate(bizEvent, isGeneratingDebtor, receipt);
            String dateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
            String blobName = String.format("%s-%s-%s-%s", TEMPLATE_PREFIX, dateFormatted, receipt.getEventId(), templateSuffix);
            PdfEngineResponse pdfEngineResponse = this.pdfEngineService.generatePDFReceipt(template, workingDirPath);
            return this.receiptBlobStorageService.saveToBlobStorage(pdfEngineResponse, blobName);
        } catch (PDFReceiptGenerationException e) {
            logger.error("An error occurred when generating or saving the PDF receipt with eventId {}", receipt.getEventId(), e);
            return PdfMetadata.builder().statusCode(e.getStatusCode()).errorMessage(e.getMessage()).build();
        }
    }
}
