package it.gov.pagopa.receipt.pdf.generator.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.generator.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptBlobClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptBlobClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartPayment;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.Payload;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.exception.CartReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.GeneratePDFException;
import it.gov.pagopa.receipt.pdf.generator.exception.PDFReceiptGenerationException;
import it.gov.pagopa.receipt.pdf.generator.exception.SavePDFToBlobException;
import it.gov.pagopa.receipt.pdf.generator.model.CartInfo;
import it.gov.pagopa.receipt.pdf.generator.model.PdfCartGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.generator.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.generator.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.model.template.ReceiptPDFTemplate;
import it.gov.pagopa.receipt.pdf.generator.service.BuildTemplateService;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateCartReceiptPdfService;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GenerateCartReceiptPdfServiceImpl implements GenerateCartReceiptPdfService {

    private final Logger logger = LoggerFactory.getLogger(GenerateCartReceiptPdfServiceImpl.class);

    private static final String TEMPLATE_PREFIX = "pagopa-ricevuta-carrello";
    private static final String PAYER_TEMPLATE_SUFFIX = "p";
    private static final String DEBTOR_TEMPLATE_SUFFIX = "d";
    private static final String ANONIMO = "ANONIMO";

    public static final int ALREADY_CREATED = 208;

    private final PdfEngineClient pdfEngineClient;
    private final ReceiptBlobClient receiptBlobClient;
    private final BuildTemplateService buildTemplateService;
    @Setter
    private long minFileLength = Long.parseLong(
            System.getenv().getOrDefault("MIN_PDF_LENGTH", "10000"));

    public GenerateCartReceiptPdfServiceImpl() {
        this.pdfEngineClient = PdfEngineClientImpl.getInstance();
        this.receiptBlobClient = ReceiptBlobClientImpl.getInstance();
        this.buildTemplateService = new BuildTemplateServiceImpl();
    }

    GenerateCartReceiptPdfServiceImpl(
            PdfEngineClient pdfEngineClient,
            ReceiptBlobClient receiptBlobClient,
            BuildTemplateService buildTemplateService
    ) {
        this.pdfEngineClient = pdfEngineClient;
        this.receiptBlobClient = receiptBlobClient;
        this.buildTemplateService = buildTemplateService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PdfCartGeneration generateCartReceipts(
            CartForReceipt cartForReceipt,
            List<BizEvent> listOfBizEvents,
            Path workingDirPath
    ) {
        PdfCartGeneration pdfCartGeneration = new PdfCartGeneration();
        Payload payload = cartForReceipt.getPayload();
        String payerCF = payload.getPayerFiscalCode();
        List<CartPayment> cart = payload.getCart();

        Map<String, CartInfo> cartInfoMap = cart.stream()
                .collect(Collectors.toMap(
                        CartPayment::getBizEventId,
                        cp -> CartInfo.builder()
                                .debtorFiscalCode(cp.getDebtorFiscalCode())
                                .subject(cp.getSubject())
                                .build()
                ));
        Map<String, BizEvent> bizEventMap = listOfBizEvents.stream()
                .collect(Collectors.toMap(
                        BizEvent::getId,
                        Function.identity()
                ));

        if (payerCF != null) {
            if (receiptAlreadyCreated(payload.getMdAttachPayer())) {
                pdfCartGeneration.setPayerMetadata(PdfMetadata.builder().statusCode(ALREADY_CREATED).build());
            } else {
                //Generate payer's complete PDF
                PdfMetadata generationResult = generateAndSavePDFReceipt(
                        listOfBizEvents,
                        false,
                        cartForReceipt.getEventId(),
                        payload.getTotalAmount(),
                        cartInfoMap,
                        workingDirPath
                );
                pdfCartGeneration.setPayerMetadata(generationResult);
            }
        }

        cart.forEach(cartPayment -> {
            String debtorFiscalCode = cartPayment.getDebtorFiscalCode();
            if (ANONIMO.equals(debtorFiscalCode) || debtorFiscalCode.equals(payerCF)) {
                return;
            }
            String bizEventId = cartPayment.getBizEventId();

            if (receiptAlreadyCreated(cartPayment.getMdAttach())) {
                pdfCartGeneration.addDebtorMetadataToMap(bizEventId, PdfMetadata.builder().statusCode(ALREADY_CREATED).build());
            } else {

                //Generate debtor's partial PDF
                PdfMetadata generationResult = generateAndSavePDFReceipt(
                        Collections.singletonList(bizEventMap.get(bizEventId)),
                        true,
                        cartForReceipt.getEventId(),
                        cartPayment.getAmount(),
                        Collections.singletonMap(bizEventId, cartInfoMap.get(bizEventId)),
                        workingDirPath
                );
                pdfCartGeneration.addDebtorMetadataToMap(bizEventId, generationResult);
            }
        });

        return pdfCartGeneration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifyAndUpdateCartReceipt(
            CartForReceipt cart,
            PdfCartGeneration pdfCartGeneration
    ) throws CartReceiptGenerationNotToRetryException {
        boolean result = true;
        Payload payload = cart.getPayload();
        PdfMetadata payerMetadata = pdfCartGeneration.getPayerMetadata();

        if (payload.getPayerFiscalCode() != null) {
            if (payerMetadata == null) {
                logger.error("Unexpected result for payer pdf cart receipt generation. Cart receipt event id {}", cart.getEventId());
                result = false;
            } else if (payerMetadata.getStatusCode() == HttpStatus.SC_OK) {
                ReceiptMetadata receiptMetadata = buildReceiptMetadata(payerMetadata);

                payload.setMdAttachPayer(receiptMetadata);
            } else if (payerMetadata.getStatusCode() != ALREADY_CREATED) {
                ReasonError reasonError = new ReasonError(payerMetadata.getStatusCode(), payerMetadata.getErrorMessage());
                payload.setReasonErrPayer(reasonError);
                result = false;
            }
        }

        boolean debtortHasNotToRetryError = false;
        Map<String, PdfMetadata> debtorMetadataMap = pdfCartGeneration.getDebtorMetadataMap();
        for (CartPayment cartPayment : payload.getCart()) {
            String debtorFiscalCode = cartPayment.getDebtorFiscalCode();
            if (idDebtorFiscalCodeValid(debtorFiscalCode, payload)) {
                continue;
            }
            PdfMetadata debtorMetadata = debtorMetadataMap.get(cartPayment.getBizEventId());
            if (debtorMetadata == null) {
                logger.error("Unexpected result for debtor of biz event id {} pdf cart receipt generation. Cart receipt id {}",
                        cart.getEventId(), cartPayment.getBizEventId());
                result = false;
            } else if (debtorMetadata.getStatusCode() == HttpStatus.SC_OK) {
                ReceiptMetadata receiptMetadata = buildReceiptMetadata(debtorMetadata);

                cartPayment.setMdAttach(receiptMetadata);
            } else if (debtorMetadata.getStatusCode() != ALREADY_CREATED) {
                if (debtorMetadata.getStatusCode() == ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode()) {
                    debtortHasNotToRetryError = true;
                }

                ReasonError reasonError = new ReasonError(debtorMetadata.getStatusCode(), debtorMetadata.getErrorMessage());
                cartPayment.setReasonErrDebtor(reasonError);
                result = false;
            }
        }
        if (hasCartReceiptGenerationNotRetriableError(payerMetadata, debtortHasNotToRetryError)) {
            String errMsg = String.format("Receipt generation fail for at least one debtor and/or payer with status: %s",
                    ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
            throw new CartReceiptGenerationNotToRetryException(errMsg);
        }

        return result;
    }

    private boolean idDebtorFiscalCodeValid(String debtorFiscalCode, Payload payload) {
        return ANONIMO.equals(debtorFiscalCode) || debtorFiscalCode.equals(payload.getPayerFiscalCode());
    }

    private boolean hasCartReceiptGenerationNotRetriableError(
            PdfMetadata payerMetadata,
            boolean debtortHasNotToRetryError
    ) {
        return (payerMetadata != null && payerMetadata.getStatusCode() == ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode())
                || debtortHasNotToRetryError;
    }

    private PdfMetadata generateAndSavePDFReceipt(
            List<BizEvent> listOfBizEvents,
            boolean requestedByDebtor,
            String eventId,
            String amount,
            Map<String, CartInfo> cartInfoMap,
            Path workingDirPath
    ) {
        try {
            ReceiptPDFTemplate template = this.buildTemplateService.buildCartTemplate(
                    listOfBizEvents,
                    requestedByDebtor,
                    eventId,
                    amount,
                    cartInfoMap
            );
            String blobName = buildBlobName(requestedByDebtor, eventId);
            PdfEngineResponse pdfEngineResponse = generatePDFReceipt(template, workingDirPath);
            return saveToBlobStorage(pdfEngineResponse, blobName);
        } catch (PDFReceiptGenerationException e) {
            logger.error("An error occurred when generating or saving the PDF cart receipt with eventId {}", eventId, e);
            return PdfMetadata.builder().statusCode(e.getStatusCode()).errorMessage(e.getMessage()).build();
        }
    }

    private String buildBlobName(boolean requestedByDebtor, String eventId) {
        String dateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String templateSuffix = requestedByDebtor ? DEBTOR_TEMPLATE_SUFFIX : PAYER_TEMPLATE_SUFFIX;
        return String.format("%s-%s-%s-%s", TEMPLATE_PREFIX, dateFormatted, eventId, templateSuffix);
    }

    private PdfMetadata saveToBlobStorage(
            PdfEngineResponse pdfEngineResponse,
            String blobName
    ) throws SavePDFToBlobException {
        String tempPdfPath = pdfEngineResponse.getTempPdfPath();

        if (new File(tempPdfPath).length() < minFileLength) {
            throw new SavePDFToBlobException("Minimum file size not reached", ReasonErrorCode.ERROR_BLOB_STORAGE.getCode());
        }

        BlobStorageResponse blobStorageResponse;
        //Save to Blob Storage
        try (BufferedInputStream pdfStream = new BufferedInputStream(new FileInputStream(tempPdfPath))) {
            blobStorageResponse = this.receiptBlobClient.savePdfToBlobStorage(pdfStream, blobName);
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

    private PdfEngineResponse generatePDFReceipt(
            ReceiptPDFTemplate template,
            Path workingDirPath
    ) throws PDFReceiptGenerationException {
        PdfEngineRequest request = new PdfEngineRequest();

        URL templateStream = GenerateCartReceiptPdfServiceImpl.class.getClassLoader().getResource("template.zip");
        //Build the request
        request.setTemplate(templateStream);
        request.setData(parseTemplateDataToString(template));
        request.setApplySignature(false);

        PdfEngineResponse pdfEngineResponse = this.pdfEngineClient.generatePDF(request, workingDirPath);

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

    private ReceiptMetadata buildReceiptMetadata(PdfMetadata payerMetadata) {
        ReceiptMetadata receiptMetadata = new ReceiptMetadata();
        receiptMetadata.setName(payerMetadata.getDocumentName());
        receiptMetadata.setUrl(payerMetadata.getDocumentUrl());
        return receiptMetadata;
    }
}
