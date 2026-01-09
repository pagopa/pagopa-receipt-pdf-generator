package it.gov.pagopa.receipt.pdf.generator.service.helpdesk.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartPayment;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.Payload;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.generator.service.helpdesk.HelpdeskService;
import it.gov.pagopa.receipt.pdf.generator.service.helpdesk.PDVTokenizerServiceRetryWrapper;
import it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.ANONIMO;
import static it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils.formatAmount;
import static it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils.getAmount;
import static it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils.getCartAmount;
import static it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils.getItemSubject;
import static it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils.getTransactionCreationDate;
import static it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils.isValidChannelOrigin;
import static it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils.isValidFiscalCode;

public class HelpdeskServiceImpl implements HelpdeskService {

    private static final Logger logger = LoggerFactory.getLogger(HelpdeskServiceImpl.class);

    private final PDVTokenizerServiceRetryWrapper pdvTokenizerService;

    public HelpdeskServiceImpl() {
        this.pdvTokenizerService = new PDVTokenizerServiceRetryWrapperImpl();
    }

    HelpdeskServiceImpl(PDVTokenizerServiceRetryWrapper pdvTokenizerService) {
        this.pdvTokenizerService = pdvTokenizerService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Receipt createReceipt(BizEvent bizEvent) {
        Receipt receipt = new Receipt();

        // Insert biz-event data into receipt
        receipt.setId(bizEvent.getId() + UUID.randomUUID());
        receipt.setEventId(bizEvent.getId());

        EventData eventData = new EventData();

        try {
            tokenizeFiscalCodes(bizEvent, receipt, eventData);
        } catch (Exception e) {
            logger.error("Error tokenizing receipt with bizEventId {}", bizEvent.getId(), e);
            receipt.setStatus(ReceiptStatusType.FAILED);
            return receipt;
        }

        eventData.setTransactionCreationDate(getTransactionCreationDate(bizEvent));
        BigDecimal amount = getAmount(bizEvent);
        eventData.setAmount(!amount.equals(BigDecimal.ZERO) ? formatAmount(amount.toString()) : null);

        CartItem item = new CartItem();
        item.setPayeeName(bizEvent.getCreditor() != null ? bizEvent.getCreditor().getCompanyName() : null);
        item.setSubject(getItemSubject(bizEvent));
        List<CartItem> cartItems = Collections.singletonList(item);
        eventData.setCart(cartItems);

        receipt.setEventData(eventData);
        return receipt;
    }

    /**
     * {@inheritDoc}
     */
    public CartForReceipt buildCart(List<BizEvent> bizEventList) throws PDVTokenizerException, JsonProcessingException {
        List<CartPayment> cartItems = new ArrayList<>();
        for (BizEvent bizEvent : bizEventList) {
            cartItems.add(buildCartPayment(bizEvent));
        }

        BizEvent bizEvent = bizEventList.get(0);
        String transactionId = bizEvent.getTransactionDetails().getTransaction().getTransactionId();
        BigDecimal amount = getCartAmount(bizEvent);
        return CartForReceipt.builder()
                .id(transactionId)
                .eventId(transactionId)
                .version("1") // this is the first version of this document
                .payload(Payload.builder()
                        .payerFiscalCode(tokenizerPayerFiscalCode(bizEvent))
                        .totalNotice(Integer.parseInt(bizEvent.getPaymentInfo().getTotalNotice()))
                        .totalAmount(!amount.equals(BigDecimal.ZERO) ? formatAmount(amount.toString()) : null)
                        .transactionCreationDate(getTransactionCreationDate(bizEvent))
                        .cart(cartItems)
                        .build())
                .build();
    }

    private CartPayment buildCartPayment(BizEvent bizEvent) throws PDVTokenizerException, JsonProcessingException {
        String debtorFiscalCode = tokenizerDebtorFiscalCode(bizEvent);
        return CartPayment.builder()
                .bizEventId(bizEvent.getId())
                .amount(formatAmount(bizEvent.getPaymentInfo().getAmount()))
                .debtorFiscalCode(debtorFiscalCode)
                .payeeName(bizEvent.getCreditor() != null ? bizEvent.getCreditor().getCompanyName() : null)
                .subject(getItemSubject(bizEvent))
                .build();
    }

    private void tokenizeFiscalCodes(
            BizEvent bizEvent,
            Receipt receipt,
            EventData eventData
    ) throws JsonProcessingException, PDVTokenizerException {
        try {
            //Tokenize Debtor
            eventData.setDebtorFiscalCode(tokenizerDebtorFiscalCode(bizEvent));
            //Tokenize Payer
            eventData.setPayerFiscalCode(tokenizerPayerFiscalCode(bizEvent));
        } catch (PDVTokenizerException e) {
            handleTokenizerException(receipt, e.getMessage(), e.getStatusCode());
            throw e;
        } catch (JsonProcessingException e) {
            handleTokenizerException(receipt, e.getMessage(), ReasonErrorCode.ERROR_PDV_MAPPING.getCode());
            throw e;
        }
    }

    private String tokenizerDebtorFiscalCode(BizEvent bizEvent) throws PDVTokenizerException, JsonProcessingException {
        return bizEvent.getDebtor() != null && isValidFiscalCode(bizEvent.getDebtor().getEntityUniqueIdentifierValue()) ?
                this.pdvTokenizerService.generateTokenForFiscalCodeWithRetry(bizEvent.getDebtor().getEntityUniqueIdentifierValue()) :
                ANONIMO;
    }

    private String tokenizerPayerFiscalCode(BizEvent bizEvent) throws PDVTokenizerException, JsonProcessingException {
        //Tokenize Payer
        if (isValidChannelOrigin(bizEvent)) {
            if (bizEvent.getTransactionDetails() != null &&
                    bizEvent.getTransactionDetails().getUser() != null &&
                    HelpdeskUtils.isValidFiscalCode(bizEvent.getTransactionDetails().getUser().getFiscalCode())
            ) {
                return this.pdvTokenizerService
                        .generateTokenForFiscalCodeWithRetry(bizEvent.getTransactionDetails().getUser().getFiscalCode());
            } else if (bizEvent.getPayer() != null && HelpdeskUtils.isValidFiscalCode(bizEvent.getPayer().getEntityUniqueIdentifierValue())) {
                return this.pdvTokenizerService
                        .generateTokenForFiscalCodeWithRetry(bizEvent.getPayer().getEntityUniqueIdentifierValue());
            }
        }
        return null;
    }

    private void handleTokenizerException(Receipt receipt, String errorMessage, int statusCode) {
        receipt.setStatus(ReceiptStatusType.FAILED);
        ReasonError reasonError = new ReasonError(statusCode, errorMessage);
        receipt.setReasonErr(reasonError);
    }
}
