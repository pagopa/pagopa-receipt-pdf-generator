package it.gov.pagopa.receipt.pdf.generator.service.impl;

import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartPayment;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.Payload;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.exception.CartReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.PDFReceiptGenerationException;
import it.gov.pagopa.receipt.pdf.generator.model.CartInfo;
import it.gov.pagopa.receipt.pdf.generator.model.PdfCartGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.model.template.ReceiptPDFTemplate;
import it.gov.pagopa.receipt.pdf.generator.service.BuildTemplateService;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateCartReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.service.PdfEngineService;
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptBlobStorageService;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.ALREADY_CREATED;
import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.ANONIMO;
import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.BLOB_NAME_DATE_PATTERN;
import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.DEBTOR_TEMPLATE_SUFFIX;
import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.PAYER_TEMPLATE_SUFFIX;
import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.TEMPLATE_PREFIX;
import static it.gov.pagopa.receipt.pdf.generator.utils.ReceiptGeneratorUtils.receiptAlreadyCreated;

public class GenerateCartReceiptPdfServiceImpl implements GenerateCartReceiptPdfService {

    private final Logger logger = LoggerFactory.getLogger(GenerateCartReceiptPdfServiceImpl.class);

    private static final String TEMPLATE_PREFIX = "pagopa-ricevuta";
    private static final String PAYER_TEMPLATE_SUFFIX = "p";
    private static final String DEBTOR_TEMPLATE_SUFFIX = "d";
    private static final String ANONIMO = "ANONIMO";

    public static final int ALREADY_CREATED = 208;

    private final PdfEngineService pdfEngineService;
    private final ReceiptBlobStorageService receiptBlobStorageService;
    private final BuildTemplateService buildTemplateService;

    public GenerateCartReceiptPdfServiceImpl() {
        this.pdfEngineService = new PdfEngineServiceImpl();
        this.receiptBlobStorageService = new ReceiptBlobStorageServiceImpl();
        this.buildTemplateService = new BuildTemplateServiceImpl();
    }

    GenerateCartReceiptPdfServiceImpl(
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
    public PdfCartGeneration generateCartReceipts(
            CartForReceipt cartForReceipt,
            List<BizEvent> listOfBizEvents,
            Path workingDirPath
    ) {
        PdfCartGeneration pdfCartGeneration = new PdfCartGeneration();
        Payload payload = cartForReceipt.getPayload();
        String payerCF = payload.getPayerFiscalCode();
        List<CartPayment> cart = payload.getCart();

        // group data for easy access during generation
        Map<String, CartInfo> cartInfoMap = groupCartInfoByBizEventId(cart);
        Map<String, BizEvent> bizEventMap = mapBizEventListById(listOfBizEvents);

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
        boolean overallSuccess = true;
        Payload payload = cart.getPayload();
        PdfMetadata payerMetadata = pdfCartGeneration.getPayerMetadata();

        if (payload.getPayerFiscalCode() != null && !processPayerMetadata(payerMetadata, payload, cart.getEventId())) {
            overallSuccess = false;
        }

        boolean debtorHasErrorOnTemplateData = false;
        Map<String, PdfMetadata> debtorMetadataMap = pdfCartGeneration.getDebtorMetadataMap();
        for (CartPayment cartPayment : payload.getCart()) {
            String debtorFiscalCode = cartPayment.getDebtorFiscalCode();

            if (isDebtorFiscalCodeToIgnore(debtorFiscalCode, payload)) {
                continue;
            }

            PdfMetadata debtorMetadata = debtorMetadataMap.get(cartPayment.getBizEventId());
            if (debtorMetadata == null) {
                logger.warn("Unexpected result for debtor of biz event id {} pdf cart receipt generation. Cart receipt id {}",
                        cart.getEventId(), cartPayment.getBizEventId());
                overallSuccess = false;
            } else if (debtorMetadata.getStatusCode() == HttpStatus.SC_OK) {
                cartPayment.setMdAttach(buildReceiptMetadata(debtorMetadata));
            } else if (debtorMetadata.getStatusCode() != ALREADY_CREATED) {
                if (debtorMetadata.getStatusCode() == ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode()) {
                    debtorHasErrorOnTemplateData = true;
                }
                cartPayment.setReasonErrDebtor(new ReasonError(debtorMetadata.getStatusCode(), debtorMetadata.getErrorMessage()));
                overallSuccess = false;
            }
        }

        boolean payerHasErrorOnTemplateData =
                payerMetadata != null
                        && payerMetadata.getStatusCode() == ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode();

        if (payerHasErrorOnTemplateData || debtorHasErrorOnTemplateData) {
            String errMsg = String.format("Receipt generation fail for at least one debtor and/or payer with status: %s",
                    ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
            throw new CartReceiptGenerationNotToRetryException(errMsg);
        }

        return overallSuccess;
    }

    private boolean processPayerMetadata(PdfMetadata payerMetadata, Payload payload, String eventId) {
        if (payerMetadata == null) {
            logger.error("Unexpected result for payer pdf cart receipt generation. Cart receipt event id {}", eventId);
            return false;
        }

        if (payerMetadata.getStatusCode() == HttpStatus.SC_OK) {
            payload.setMdAttachPayer(buildReceiptMetadata(payerMetadata));
            return true;
        }

        if (payerMetadata.getStatusCode() != ALREADY_CREATED) {
            ReasonError reasonError = new ReasonError(payerMetadata.getStatusCode(), payerMetadata.getErrorMessage());
            payload.setReasonErrPayer(reasonError);
            return false;
        }
        return true;
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
            String blobName = buildBlobName(requestedByDebtor, eventId, listOfBizEvents);
            PdfEngineResponse pdfEngineResponse = this.pdfEngineService.generatePDFReceipt(template, workingDirPath);
            return this.receiptBlobStorageService.saveToBlobStorage(pdfEngineResponse, blobName);
        } catch (PDFReceiptGenerationException e) {
            logger.error("An error occurred when generating or saving the PDF cart receipt with eventId {}", eventId, e);
            return PdfMetadata.builder().statusCode(e.getStatusCode()).errorMessage(e.getMessage()).build();
        }
    }

    private String buildBlobName(boolean requestedByDebtor, String eventId, List<BizEvent> listOfBizEvents) {
        String dateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern(BLOB_NAME_DATE_PATTERN));
        String id = requestedByDebtor ? listOfBizEvents.get(0).getId() : eventId;
        String templateSuffix = requestedByDebtor ? DEBTOR_TEMPLATE_SUFFIX : PAYER_TEMPLATE_SUFFIX;
        return String.format("%s-%s-%s-%s-c", TEMPLATE_PREFIX, dateFormatted, id, templateSuffix);
    }

    private boolean isDebtorFiscalCodeToIgnore(String debtorFiscalCode, Payload payload) {
        return ANONIMO.equals(debtorFiscalCode) || debtorFiscalCode.equals(payload.getPayerFiscalCode());
    }

    private ReceiptMetadata buildReceiptMetadata(PdfMetadata payerMetadata) {
        ReceiptMetadata receiptMetadata = new ReceiptMetadata();
        receiptMetadata.setName(payerMetadata.getDocumentName());
        receiptMetadata.setUrl(payerMetadata.getDocumentUrl());
        return receiptMetadata;
    }

    private Map<String, BizEvent> mapBizEventListById(List<BizEvent> listOfBizEvents) {
        return listOfBizEvents.stream()
                .collect(Collectors.toMap(
                        BizEvent::getId,
                        Function.identity()
                ));
    }

    private Map<String, CartInfo> groupCartInfoByBizEventId(List<CartPayment> cart) {
        return cart.stream()
                .collect(Collectors.toMap(
                        CartPayment::getBizEventId,
                        cp -> CartInfo.builder()
                                .debtorFiscalCode(cp.getDebtorFiscalCode())
                                .subject(cp.getSubject())
                                .build()
                ));
    }
}
