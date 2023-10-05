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
import it.gov.pagopa.receipt.pdf.generator.exception.PDFReceiptGenerationException;
import it.gov.pagopa.receipt.pdf.generator.exception.SavePDFToBlobException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.generator.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.generator.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.model.template.*;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.utils.BizEventToPdfMapper;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

public class GenerateReceiptPdfServiceImpl implements GenerateReceiptPdfService {

    private final Logger logger = LoggerFactory.getLogger(GenerateReceiptPdfServiceImpl.class);

    private static final String TEMPLATE_PREFIX = "pagopa-ricevuta";
    private static final String PAYER_TEMPLATE_SUFFIX = "p";
    private static final String DEBTOR_TEMPLATE_SUFFIX = "d";

    public static final int ALREADY_CREATED = 208;

    private final PdfEngineClient pdfEngineClient;
    private final ReceiptBlobClient receiptBlobClient;

    public GenerateReceiptPdfServiceImpl() {
        this.pdfEngineClient = PdfEngineClientImpl.getInstance();
        this.receiptBlobClient = ReceiptBlobClientImpl.getInstance();
    }

    public GenerateReceiptPdfServiceImpl(PdfEngineClient pdfEngineClient, ReceiptBlobClient receiptBlobClient) {
        this.pdfEngineClient = pdfEngineClient;
        this.receiptBlobClient = receiptBlobClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PdfGeneration generateReceipts(Receipt receipt, BizEvent bizEvent) {
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
                ReceiptPDFTemplate completeTemplate = buildTemplate(bizEvent, false);
                PdfMetadata generationResult = generateAndSavePDFReceipt(bizEvent, PAYER_TEMPLATE_SUFFIX, completeTemplate);
                pdfGeneration.setDebtorMetadata(generationResult);
                return pdfGeneration;
            }

            //Generate payer's complete PDF
            if (receiptAlreadyCreated(receipt.getMdAttachPayer())) {
                pdfGeneration.setPayerMetadata(PdfMetadata.builder().statusCode(ALREADY_CREATED).build());
            } else {
                ReceiptPDFTemplate completeTemplate = buildTemplate(bizEvent, false);

                PdfMetadata generationResult = generateAndSavePDFReceipt(bizEvent, PAYER_TEMPLATE_SUFFIX, completeTemplate);
                pdfGeneration.setPayerMetadata(generationResult);
            }
        } else {
            pdfGeneration.setGenerateOnlyDebtor(true);
        }

        //Generate debtor's partial PDF
        if (receiptAlreadyCreated(receipt.getMdAttach())) {
            pdfGeneration.setDebtorMetadata(PdfMetadata.builder().statusCode(ALREADY_CREATED).build());
        } else {
            ReceiptPDFTemplate onlyDebtorTemplate = buildTemplate(bizEvent, true);

            PdfMetadata generationResult = generateAndSavePDFReceipt(bizEvent, DEBTOR_TEMPLATE_SUFFIX, onlyDebtorTemplate);
            pdfGeneration.setDebtorMetadata(generationResult);
        }

        return pdfGeneration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifyAndUpdateReceipt(Receipt receipt, PdfGeneration pdfGeneration) {
        PdfMetadata debtorMetadata = pdfGeneration.getDebtorMetadata();
        boolean result = true;
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

        if (pdfGeneration.isGenerateOnlyDebtor()) {
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
            ReasonError reasonError = new ReasonError(payerMetadata.getStatusCode(), payerMetadata.getErrorMessage());
            receipt.setReasonErr(reasonError);
            result = false;
        }
        return result;
    }

    private PdfMetadata generateAndSavePDFReceipt(BizEvent bizEvent, String templateSuffix, ReceiptPDFTemplate completeTemplate) {
        try {
            String dateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
            String blobName = String.format("%s-%s-%s-%s", TEMPLATE_PREFIX, dateFormatted, bizEvent.getId(), templateSuffix);
            PdfEngineResponse pdfEngineResponse = generatePdf(completeTemplate);
            return saveToBlobStorage(pdfEngineResponse, blobName);
        } catch (PDFReceiptGenerationException e) {
            logger.error("An error occurred when generating or saving the PDF receipt for biz-event {}", bizEvent.getId(), e);
            return PdfMetadata.builder().statusCode(e.getStatusCode()).errorMessage(e.getMessage()).build();
        }
    }

    private PdfMetadata saveToBlobStorage(PdfEngineResponse pdfEngineResponse, String blobName) throws SavePDFToBlobException {
        String tempPdfPath = pdfEngineResponse.getTempPdfPath();
        String tempDirectoryPath = pdfEngineResponse.getTempDirectoryPath();

        BlobStorageResponse blobStorageResponse;
        //Save to Blob Storage
        try (BufferedInputStream pdfStream = new BufferedInputStream(new FileInputStream(tempPdfPath))) {
            blobStorageResponse = receiptBlobClient.savePdfToBlobStorage(pdfStream, blobName);
        } catch (Exception e) {
            throw new SavePDFToBlobException("Error saving pdf to blob storage", ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), e);
        } finally {
            deleteTempFolderAndFile(tempPdfPath, tempDirectoryPath);
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

    // TODO fix temp files create a temp dir and pass it to the client
    private PdfEngineResponse generatePdf(ReceiptPDFTemplate template) throws PDFReceiptGenerationException {
        PdfEngineRequest request = new PdfEngineRequest();

        URL templateStream = GenerateReceiptPdfServiceImpl.class.getClassLoader().getResource("template.zip");
        //Build the request
        request.setTemplate(templateStream);
        request.setData(parseTemplateDataToString(template));
        request.setApplySignature(false);

        PdfEngineResponse pdfEngineResponse = pdfEngineClient.generatePDF(request);

        if (pdfEngineResponse.getStatusCode() != HttpStatus.SC_OK) {
            throw new GeneratePDFException(pdfEngineResponse.getErrorMessage(), pdfEngineResponse.getStatusCode());
        }

        return pdfEngineResponse;
    }

    private ReceiptPDFTemplate buildTemplate(BizEvent bizEvent, boolean partialTemplate) {
        return ReceiptPDFTemplate.builder()
                .transaction(Transaction.builder()
                        .id(BizEventToPdfMapper.getId(bizEvent))
                        .timestamp(BizEventToPdfMapper.getTimestamp(bizEvent))
                        .amount(BizEventToPdfMapper.getAmount(bizEvent))
                        .psp(BizEventToPdfMapper.getPsp(bizEvent))
                        .rrn(BizEventToPdfMapper.getRnn(bizEvent))
                        .paymentMethod(PaymentMethod.builder()
                                .name(BizEventToPdfMapper.getPaymentMethodName(bizEvent))
                                .logo(BizEventToPdfMapper.getPaymentMethodLogo(bizEvent))
                                .accountHolder(BizEventToPdfMapper.getPaymentMethodAccountHolder(bizEvent))
                                .build())
                        .authCode(BizEventToPdfMapper.getAuthCode(bizEvent))
                        .requestedByDebtor(partialTemplate)
                        .build())
                .user(partialTemplate ?
                        null :
                        User.builder()
                                .data(UserData.builder()
                                        .fullName(BizEventToPdfMapper.getUserFullName(bizEvent))
                                        .taxCode(BizEventToPdfMapper.getUserTaxCode(bizEvent))
                                        .build())
                                .email(BizEventToPdfMapper.getUserMail())
                                .build())
                .cart(Cart.builder()
                        .items(Collections.singletonList(
                                Item.builder()
                                        .refNumber(RefNumber.builder()
                                                .type(BizEventToPdfMapper.getRefNumberType(bizEvent))
                                                .value(BizEventToPdfMapper.getRefNumberValue(bizEvent))
                                                .build())
                                        .debtor(Debtor.builder()
                                                .fullName(BizEventToPdfMapper.getDebtorFullName(bizEvent))
                                                .taxCode(BizEventToPdfMapper.getDebtorTaxCode(bizEvent))
                                                .build())
                                        .payee(Payee.builder()
                                                .name(BizEventToPdfMapper.getPayeeName(bizEvent))
                                                .taxCode(BizEventToPdfMapper.getPayeeTaxCode(bizEvent))
                                                .build())
                                        .subject(BizEventToPdfMapper.getItemSubject(bizEvent))
                                        .amount(BizEventToPdfMapper.getItemAmount(bizEvent))
                                        .build()
                        ))
                        //Cart items total amount w/o fee, TODO change it with multiple cart items implementation
                        .amountPartial(BizEventToPdfMapper.getItemAmount(bizEvent))
                        .build())
                .build();
    }

    private void deleteTempFolderAndFile(String tempPdfPath, String tempDirectoryPath) {
        File tempFile = new File(tempPdfPath);
        if (tempFile.exists()) {
            try {
                Files.delete(tempFile.toPath());
            } catch (IOException e) {
                logger.warn("Error deleting temporary pdf file from file system", e);
            }
        }

        File tempDirectory = new File(tempDirectoryPath);
        if (tempDirectory.exists()) {
            try {
                Files.delete(tempDirectory.toPath());
            } catch (IOException e) {
                logger.warn("Error deleting temporary pdf directory from file system", e);
            }
        }
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
