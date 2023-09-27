package it.gov.pagopa.receipt.pdf.generator.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import lombok.NoArgsConstructor;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;

@NoArgsConstructor
public class GenerateReceiptPdfServiceImpl implements GenerateReceiptPdfService {

    private final Logger logger = LoggerFactory.getLogger(GenerateReceiptPdfServiceImpl.class);

    private static final int ALREADY_CREATED = 208;

    /**
     * {@inheritDoc}
     */
    @Override
    public PdfGeneration generateReceipts(Receipt receipt, BizEvent bizEvent) {
        PdfGeneration pdfGeneration = new PdfGeneration();

        String debtorCF = receipt.getEventData().getDebtorFiscalCode();
        String payerCF = receipt.getEventData().getPayerFiscalCode();

        if (payerCF == null || payerCF.equals(debtorCF)) {
            pdfGeneration.setGenerateOnlyDebtor(true);
            //Generate debtor's complete PDF
            if (receiptAlreadyCreated(receipt.getMdAttach())) {
                pdfGeneration.setDebtorMetadata(PdfMetadata.builder().statusCode(ALREADY_CREATED).build());
                return pdfGeneration;
            }
            ReceiptPDFTemplate completeTemplate = buildCompleteReceipt(bizEvent);
            PdfMetadata generationResult = generateAndSavePDFReceipt(bizEvent, debtorCF, completeTemplate);
            pdfGeneration.setDebtorMetadata(generationResult);
            return pdfGeneration;
        }

        //Generate debtor's partial PDF
        if (receiptAlreadyCreated(receipt.getMdAttach())) {
            pdfGeneration.setDebtorMetadata(PdfMetadata.builder().statusCode(ALREADY_CREATED).build());
        } else {
            ReceiptPDFTemplate onlyDebtorTemplate = buildOnlyDebtorTemplate(bizEvent);

            PdfMetadata generationResult = generateAndSavePDFReceipt(bizEvent, debtorCF, onlyDebtorTemplate);
            pdfGeneration.setDebtorMetadata(generationResult);
        }
        //Generate payer's complete PDF
        if (receiptAlreadyCreated(receipt.getMdAttachPayer())) {
            pdfGeneration.setPayerMetadata(PdfMetadata.builder().statusCode(ALREADY_CREATED).build());
        } else {
            ReceiptPDFTemplate completeTemplate = buildCompleteReceipt(bizEvent);

            PdfMetadata generationResult = generateAndSavePDFReceipt(bizEvent, payerCF, completeTemplate);
            pdfGeneration.setPayerMetadata(generationResult);
        }
        return pdfGeneration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifyAndUpdateReceipt(Receipt receipt, PdfGeneration pdfGeneration) {
        PdfMetadata debtorMetadata = pdfGeneration.getDebtorMetadata();
        if (pdfGeneration.isGenerateOnlyDebtor()) {
            if (debtorMetadata == null) {
                logger.error("Unexpected result for debtor pdf receipt generation. Receipt id {}", receipt.getId());
                return false;
            }
            if (debtorMetadata.getStatusCode() == ALREADY_CREATED) {
                return true;
            }
            if (debtorMetadata.getStatusCode() == HttpStatus.SC_OK) {
                ReceiptMetadata receiptMetadata = new ReceiptMetadata();
                receiptMetadata.setName(debtorMetadata.getDocumentName());
                receiptMetadata.setUrl(debtorMetadata.getDocumentUrl());

                receipt.setMdAttach(receiptMetadata);
                return true;
            }
            ReasonError reasonError = new ReasonError(debtorMetadata.getStatusCode(), debtorMetadata.getErrorMessage());
            receipt.setReasonErr(reasonError);
            return false;
        }

        // No single receipt
        PdfMetadata payerMetadata = pdfGeneration.getPayerMetadata();
        boolean result = true;
        if (debtorMetadata == null || payerMetadata == null) {
            logger.error("Unexpected result for both payer and debtor pdf receipt generation. Receipt id {}", receipt.getId());
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

    private PdfMetadata generateAndSavePDFReceipt(BizEvent bizEvent, String fiscalCode, ReceiptPDFTemplate completeTemplate) {
        try {
            String blobName = bizEvent.getId() + fiscalCode;
            PdfEngineResponse pdfEngineResponse = generatePdf(completeTemplate);
            return saveToBlobStorage(pdfEngineResponse, blobName);
        } catch (PDFReceiptGenerationException e) {
            logger.error("An error occurred when generating or saving the PDF receipt for biz-event {}", bizEvent.getId(), e);
            return PdfMetadata.builder().statusCode(e.getStatusCode()).errorMessage(e.getMessage()).build();
        }
    }

    private PdfMetadata saveToBlobStorage(PdfEngineResponse pdfEngineResponse, String blobName) throws SavePDFToBlobException {
        ReceiptBlobClientImpl blobClient = ReceiptBlobClientImpl.getInstance();
        String tempPdfPath = pdfEngineResponse.getTempPdfPath();
        String tempDirectoryPath = pdfEngineResponse.getTempDirectoryPath();

        BlobStorageResponse blobStorageResponse;
        //Save to Blob Storage
        try (BufferedInputStream pdfStream = new BufferedInputStream(new FileInputStream(tempPdfPath))) {
            blobStorageResponse = blobClient.savePdfToBlobStorage(pdfStream, blobName);
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

        PdfEngineClientImpl pdfEngineClient = PdfEngineClientImpl.getInstance();

        //Call the PDF Engine
        PdfEngineResponse pdfEngineResponse = pdfEngineClient.generatePDF(request);

        if (pdfEngineResponse.getStatusCode() != HttpStatus.SC_OK) {
            throw new GeneratePDFException(pdfEngineResponse.getErrorMessage(), pdfEngineResponse.getStatusCode());
        }

        return pdfEngineResponse;
    }

    private ReceiptPDFTemplate buildOnlyDebtorTemplate(BizEvent bizEvent) {
        // TODO build template data
        return ReceiptPDFTemplate.builder()
                .transaction(Transaction.builder()
                        .id("F57E2F8E-25FF-4183-AB7B-4A5EC1A96644")
                        .timestamp("2020-07-10 15:00:00.000")
                        .amount(300.00)
                        .psp(PSP.builder()
                                .name("Nexi")
                                .fee(PSPFee.builder()
                                        .amount(2.00)
                                        .build())
                                .build())
                        .rrn("1234567890")
                        .paymentMethod(PaymentMethod.builder()
                                .name("Visa *1234")
                                .logo("https://...")
                                .accountHolder("Marzia Roccaraso")
                                .extraFee(false)
                                .build())
                        .authCode("9999999999")
                        .build())
                .cart(Cart.builder()
                        .items(Collections.singletonList(
                                Item.builder()
                                        .refNumber(RefNumber.builder()
                                                .type("codiceAvviso")
                                                .value("123456789012345678")
                                                .build())
                                        .debtor(Debtor.builder()
                                                .fullName("Giuseppe Bianchi")
                                                .taxCode("BNCGSP70A12F205X")
                                                .build())
                                        .payee(Payee.builder()
                                                .name("Comune di Controguerra")
                                                .taxCode("82001760675")
                                                .build())
                                        .subject("TARI 2022")
                                        .amount(150.00)
                                        .build()
                        ))
                        .build())
                .build();
    }

    private ReceiptPDFTemplate buildCompleteReceipt(BizEvent bizEvent) {
        // TODO build template data
        return ReceiptPDFTemplate.builder()
                .transaction(Transaction.builder()
                        .id("F57E2F8E-25FF-4183-AB7B-4A5EC1A96644")
                        .timestamp("2020-07-10 15:00:00.000")
                        .amount(300.00)
                        .psp(PSP.builder()
                                .name("Nexi")
                                .fee(PSPFee.builder()
                                        .amount(2.00)
                                        .build())
                                .build())
                        .rrn("1234567890")
                        .paymentMethod(PaymentMethod.builder()
                                .name("Visa *1234")
                                .logo("https://...")
                                .accountHolder("Marzia Roccaraso")
                                .extraFee(false)
                                .build())
                        .authCode("9999999999")
                        .build())
                .user(User.builder()
                        .data(UserData.builder()
                                .firstName("Marzia")
                                .lastName("Roccaraso")
                                .taxCode("RCCMRZ88A52C409A")
                                .build())
                        .email("email@test.it")
                        .build())
                .cart(Cart.builder()
                        .items(Collections.singletonList(
                                Item.builder()
                                        .refNumber(RefNumber.builder()
                                                .type("codiceAvviso")
                                                .value("123456789012345678")
                                                .build())
                                        .debtor(Debtor.builder()
                                                .fullName("Giuseppe Bianchi")
                                                .taxCode("BNCGSP70A12F205X")
                                                .build())
                                        .payee(Payee.builder()
                                                .name("Comune di Controguerra")
                                                .taxCode("82001760675")
                                                .build())
                                        .subject("TARI 2022")
                                        .amount(150.00)
                                        .build()
                        ))
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
        return receiptMetadata != null && receiptMetadata.getUrl() != null && receiptMetadata.getUrl().isEmpty();
    }
}
