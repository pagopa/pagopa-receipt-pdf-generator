package it.gov.pagopa.receipt.pdf.generator.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.event.Info;
import it.gov.pagopa.receipt.pdf.generator.entity.event.InfoTransaction;
import it.gov.pagopa.receipt.pdf.generator.entity.event.TransactionDetails;
import it.gov.pagopa.receipt.pdf.generator.entity.event.WalletItem;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.exception.PdfJsonMappingException;
import it.gov.pagopa.receipt.pdf.generator.exception.TemplateDataMappingException;
import it.gov.pagopa.receipt.pdf.generator.model.CartInfo;
import it.gov.pagopa.receipt.pdf.generator.model.enumeration.TemplateDatasource;
import it.gov.pagopa.receipt.pdf.generator.model.template.*;
import it.gov.pagopa.receipt.pdf.generator.service.BuildTemplateService;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import it.gov.pagopa.receipt.pdf.generator.utils.TemplateDataField;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BuildTemplateServiceImpl implements BuildTemplateService {


    private static final String REF_TYPE_NOTICE = "codiceAvviso";
    private static final String REF_TYPE_IUV = "IUV";

    private static final String PAYMENT_METHOD_NAME_KEY = "PAYMENT_METHOD_NAME_MAP";
    private static final String BRAND_LOGO_MAP_ENV_KEY = "BRAND_LOGO_MAP";
    private static final String PSP_CONFIG_FILE_JSON_FILE_NAME = "psp_config_file.json";
    private static final String RECEIPT_DATE_FORMAT = "dd MMMM yyyy, HH:mm:ss";

    /**
     * Hide from public usage.
     */

    private static final Map<String, String> paymentMethodNameMap;
    private static final Map<String, String> brandLogoMap;
    private static final Map<String, Object> pspMap;
    public static final String MODEL_TYPE_IUV = "1";
    public static final String MODEL_TYPE_NOTICE = "2";
    public static final String DEBTOR_ANONIMO_CF = "ANONIMO";

    public static final String WISP_REGEX = "^351.*";

    static {
        try {
            paymentMethodNameMap = ObjectMapperUtils.mapString(System.getenv().get(PAYMENT_METHOD_NAME_KEY), Map.class);
        } catch (JsonProcessingException e) {
            throw new PdfJsonMappingException(e);
        }

    }

    static {
        try {
            brandLogoMap = ObjectMapperUtils.mapString(System.getenv().get(BRAND_LOGO_MAP_ENV_KEY), Map.class);
        } catch (JsonProcessingException e) {
            throw new PdfJsonMappingException(e);
        }

    }

    static {
        try (InputStream data = BuildTemplateServiceImpl.class.getClassLoader().getResourceAsStream(PSP_CONFIG_FILE_JSON_FILE_NAME)) {
            if (data == null) {
                throw new IOException("PSP config file not found");
            }
            pspMap = ObjectMapperUtils.mapString(new String(data.readAllBytes()), Map.class);
        } catch (IOException e) {
            throw new PdfJsonMappingException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReceiptPDFTemplate buildTemplate(
            BizEvent bizEvent,
            boolean requestedByDebtor,
            Receipt receipt
    ) throws TemplateDataMappingException {
        return commonTemplate(
                Collections.singletonList(bizEvent),
                requestedByDebtor,
                getServiceCustomerId(receipt),
                getAmount(receipt),
                Collections.singletonMap(
                        receipt.getEventId(),
                        CartInfo.builder()
                                .debtorFiscalCode(receipt.getEventData().getDebtorFiscalCode())
                                .subject(getItemSubject(receipt))
                                .build()
                )
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReceiptPDFTemplate buildCartTemplate(
            List<BizEvent> listOfBizEvents,
            boolean requestedByDebtor,
            String eventId,
            String amount,
            Map<String, CartInfo> cartInfoMap
    ) throws TemplateDataMappingException {
        ReceiptPDFTemplate commonTemplate = commonTemplate(
                listOfBizEvents,
                requestedByDebtor,
                validateCartServiceCustomerId(eventId),
                getCartAmount(amount),
                cartInfoMap
        );

        // hideFeesAndTotals in case is a cart receipt but requested by one of the debtors
        commonTemplate.getCart().setHideFeesAndTotals(requestedByDebtor);

        return commonTemplate;
    }

    private ReceiptPDFTemplate commonTemplate(
            List<BizEvent> listOfBizEvents,
            boolean requestedByDebtor,
            String serviceCustomerId,
            String amount,
            Map<String, CartInfo> cartInfoMap
    ) throws TemplateDataMappingException {
        BizEvent bizEvent = listOfBizEvents.get(0);
        if (bizEvent == null) {
            throw new TemplateDataMappingException(
                    "Error mapping bizEvent data to template, bizEvent is null",
                    ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode()
            );
        }

        return ReceiptPDFTemplate.builder()
                .serviceCustomerId(serviceCustomerId)
                .transaction(Transaction.builder()
                        .timestamp(getTimestamp(bizEvent))
                        .amount(amount)
                        .psp(getPsp(bizEvent))
                        .rrn(getRnn(bizEvent))
                        .paymentMethod(PaymentMethod.builder()
                                .name(getPaymentMethodName(bizEvent))
                                .logo(getPaymentMethodLogo(bizEvent))
                                .accountHolder(getPaymentMethodAccountHolder(bizEvent))
                                .build())
                        .authCode(getAuthCode(bizEvent))
                        .requestedByDebtor(requestedByDebtor)
                        .processedByPagoPA(getProcessedByPagoPA(bizEvent))
                        .build())
                .user(requestedByDebtor ?
                        null :
                        User.builder()
                                .data(UserData.builder()
                                        .fullName(getUserFullName(bizEvent))
                                        .taxCode(getUserTaxCode(bizEvent))
                                        .build())
                                .build())
                .cart(buildCart(listOfBizEvents, cartInfoMap))
                .build();
    }

    private Cart buildCart(
            List<BizEvent> listOfBizEvents,
            Map<String, CartInfo> cartInfoMap
    ) throws TemplateDataMappingException {
        double amountPartial = 0;
        List<Item> cartItems = new ArrayList<>();
        for (BizEvent bizEvent : listOfBizEvents) {
            CartInfo cartInfo = cartInfoMap.get(bizEvent.getId());
            cartItems.add(
                    Item.builder()
                            .refNumber(RefNumber.builder()
                                    .type(getRefNumberType(bizEvent))
                                    .value(getRefNumberValue(bizEvent))
                                    .build())
                            .debtor(DEBTOR_ANONIMO_CF.equals(cartInfo.getDebtorFiscalCode()) ?
                                    null : Debtor.builder()
                                    .fullName(getDebtorFullName(bizEvent))
                                    .taxCode(getDebtorTaxCode(bizEvent))
                                    .build())
                            .payee(Payee.builder()
                                    .name(getPayeeName(bizEvent))
                                    .taxCode(getPayeeTaxCode(bizEvent))
                                    .build())
                            .subject(cartInfo.getSubject())
                            .amount(getItemAmount(bizEvent, true))
                            .build()
            );
            amountPartial = amountPartial + Double.parseDouble(getItemAmount(bizEvent, false));
        }
        return Cart.builder()
                .items(cartItems)
                .amountPartial(currencyFormat(String.valueOf(amountPartial)))
                .build();
    }

    private String getServiceCustomerId(Receipt receipt) throws TemplateDataMappingException {
        if (receipt.getEventId() != null) {
            return receipt.getEventId();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.SERVICE_CUSTOMER_ID, TemplateDatasource.RECEIPT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String validateCartServiceCustomerId(String eventId) throws TemplateDataMappingException {
        if (eventId != null) {
            return eventId;
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.SERVICE_CUSTOMER_ID, TemplateDatasource.CART), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getTimestamp(BizEvent event) throws TemplateDataMappingException {
        if (
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getCreationDate() != null
        ) {
            return dateFormatZoned(event.getTransactionDetails().getTransaction().getCreationDate());
        }
        if (event.getPaymentInfo() != null && event.getPaymentInfo().getPaymentDateTime() != null) {
            return dateFormat(event.getPaymentInfo().getPaymentDateTime());
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.TRANSACTION_TIMESTAMP, TemplateDatasource.BIZ_EVENT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getAmount(Receipt receipt) throws TemplateDataMappingException {
        if (receipt.getEventData() != null &&
                receipt.getEventData().getAmount() != null) {
            return receipt.getEventData().getAmount();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.TRANSACTION_AMOUNT, TemplateDatasource.RECEIPT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getCartAmount(String amount) throws TemplateDataMappingException {
        if (amount != null) {
            return amount;
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.TRANSACTION_AMOUNT, TemplateDatasource.CART), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getRnn(BizEvent event) throws TemplateDataMappingException {
        if (
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getRrn() != null
        ) {
            return event.getTransactionDetails().getTransaction().getRrn();
        }
        if (event.getPaymentInfo() != null) {
            if (event.getPaymentInfo().getPaymentToken() != null) {
                return event.getPaymentInfo().getPaymentToken();
            }
            if (event.getPaymentInfo().getIUR() != null) {
                return event.getPaymentInfo().getIUR();
            }
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.TRANSACTION_RRN, TemplateDatasource.BIZ_EVENT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getAuthCode(BizEvent event) {
        if (event.getTransactionDetails() != null && event.getTransactionDetails().getTransaction() != null) {
            return event.getTransactionDetails().getTransaction().getNumAut();
        }
        return null;
    }

    private String getPaymentMethodName(BizEvent event) {
        String paymentMethodType = Optional.ofNullable(event.getTransactionDetails())
                .map(TransactionDetails::getWallet)
                .map(WalletItem::getInfo)
                .map(Info::getType)
                .or(() -> Optional.ofNullable(event.getTransactionDetails())
                        .map(TransactionDetails::getInfo)
                        .map(InfoTransaction::getType))
                .orElse(null);
        return paymentMethodNameMap.getOrDefault(paymentMethodType, null);
    }

    private String getPaymentMethod(BizEvent event) {
        return Optional.ofNullable(event.getTransactionDetails())
                .map(TransactionDetails::getWallet)
                .map(WalletItem::getInfo)
                .map(Info::getBrand)
                .or(() -> Optional.ofNullable(event.getTransactionDetails())
                        .map(TransactionDetails::getInfo)
                        .map(InfoTransaction::getBrand))
                .orElse(null);
    }

    private String getPaymentMethodLogo(BizEvent event) {
        return Optional.ofNullable(event.getTransactionDetails())
                .map(TransactionDetails::getInfo)
                .map(InfoTransaction::getBrandLogo)
                .orElseGet(() -> brandLogoMap.getOrDefault(getPaymentMethod(event), null));
    }

    private String getPaymentMethodAccountHolder(BizEvent event) {
        if (
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getWallet() != null &&
                        event.getTransactionDetails().getWallet().getInfo() != null &&
                        event.getTransactionDetails().getWallet().getInfo().getHolder() != null
        ) {
            return event.getTransactionDetails().getWallet().getInfo().getHolder();
        }
        return null;
    }

    private String getUserFullName(BizEvent event) {
        if (
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getUser() != null &&
                        event.getTransactionDetails().getUser().getName() != null &&
                        event.getTransactionDetails().getUser().getSurname() != null
        ) {
            return String.format("%s %s",
                    event.getTransactionDetails().getUser().getName(),
                    event.getTransactionDetails().getUser().getSurname());
        }
        if (event.getPayer() != null && event.getPayer().getFullName() != null) {
            return event.getPayer().getFullName();
        }
        return "";
    }

    private String getUserTaxCode(BizEvent event) throws TemplateDataMappingException {
        if (
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getUser() != null &&
                        event.getTransactionDetails().getUser().getFiscalCode() != null
        ) {
            return event.getTransactionDetails().getUser().getFiscalCode();
        }
        if (event.getPayer() != null && event.getPayer().getEntityUniqueIdentifierValue() != null) {
            return event.getPayer().getEntityUniqueIdentifierValue();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.USER_DATA_TAX_CODE, TemplateDatasource.BIZ_EVENT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getRefNumberType(BizEvent event) throws TemplateDataMappingException {
        if (event.getDebtorPosition() != null && event.getDebtorPosition().getModelType() != null) {
            if (event.getDebtorPosition().getModelType().equals(MODEL_TYPE_IUV) || (
                    event.getDebtorPosition().getNoticeNumber() != null &&
                            event.getDebtorPosition().getNoticeNumber().matches(WISP_REGEX) &&
                            event.getDebtorPosition().getIuv() != null)) {
                return REF_TYPE_IUV;
            }
            if (event.getDebtorPosition().getModelType().equals(MODEL_TYPE_NOTICE)) {
                return REF_TYPE_NOTICE;
            }
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.CART_ITEM_REF_NUMBER_TYPE, TemplateDatasource.BIZ_EVENT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getRefNumberValue(BizEvent event) throws TemplateDataMappingException {
        if (event.getDebtorPosition() != null && event.getDebtorPosition().getModelType() != null) {
            if ((event.getDebtorPosition().getModelType().equals(MODEL_TYPE_IUV) || (
                    event.getDebtorPosition().getNoticeNumber() != null &&
                            event.getDebtorPosition().getNoticeNumber().matches(WISP_REGEX))) &&
                    event.getDebtorPosition().getIuv() != null) {
                return event.getDebtorPosition().getIuv();
            }
            if (event.getDebtorPosition().getModelType().equals(MODEL_TYPE_NOTICE) && event.getDebtorPosition().getNoticeNumber() != null) {
                return event.getDebtorPosition().getNoticeNumber();
            }
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.CART_ITEM_REF_NUMBER_VALUE, TemplateDatasource.BIZ_EVENT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getDebtorFullName(BizEvent event) {
        return event.getDebtor() != null ? formatFullName(event.getDebtor().getFullName(), event.getDebtor().getEntityUniqueIdentifierValue()) : null;
    }

    private String getDebtorTaxCode(BizEvent event) throws TemplateDataMappingException {
        if (event.getDebtor() != null && event.getDebtor().getEntityUniqueIdentifierValue() != null) {
            return event.getDebtor().getEntityUniqueIdentifierValue();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.CART_ITEM_DEBTOR_TAX_CODE, TemplateDatasource.BIZ_EVENT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getPayeeName(BizEvent event) {
        return event.getCreditor() != null ? event.getCreditor().getCompanyName() : null;
    }

    private String getPayeeTaxCode(BizEvent event) throws TemplateDataMappingException {
        if (event.getCreditor() != null && event.getCreditor().getIdPA() != null) {
            return event.getCreditor().getIdPA();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.CART_ITEM_PAYEE_TAX_CODE, TemplateDatasource.BIZ_EVENT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getItemSubject(Receipt receipt) throws TemplateDataMappingException {
        if (receipt.getEventData() != null &&
                !receipt.getEventData().getCart().isEmpty() &&
                receipt.getEventData().getCart().get(0) != null &&
                receipt.getEventData().getCart().get(0).getSubject() != null
        ) {
            return receipt.getEventData().getCart().get(0).getSubject();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.CART_ITEM_SUBJECT, TemplateDatasource.RECEIPT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getItemAmount(BizEvent event, boolean currencyFormatted) throws TemplateDataMappingException {
        if (event.getPaymentInfo() != null && event.getPaymentInfo().getAmount() != null) {
            return currencyFormatted ? currencyFormat(event.getPaymentInfo().getAmount()) : event.getPaymentInfo().getAmount();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.CART_ITEM_AMOUNT, TemplateDatasource.BIZ_EVENT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getPspFee(BizEvent event) {
        if (
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getFee() != 0L
        ) {
            // Fee in transactionDetails is defined in cents (es. 25500 not 255.00)
            return currencyFormat(String.valueOf(event.getTransactionDetails().getTransaction().getFee() / 100.00));
        }
        return null;
    }

    private PSP getPsp(BizEvent event) throws TemplateDataMappingException {
        if (event.getPsp() != null && event.getPsp().getIdPsp() != null) {
            LinkedHashMap<String, String> info = (LinkedHashMap<String, String>) pspMap
                    .getOrDefault(event.getPsp().getIdPsp(), new LinkedHashMap<>());
            String pspFee = getPspFee(event);
            return PSP.builder()
                    .name(getOrThrow(info, "name", TemplateDataField.TRANSACTION_PSP_NAME))
                    .fee(PSPFee.builder()
                            .amount(pspFee)
                            .build())
                    .companyName(getOrThrow(info, "companyName", TemplateDataField.TRANSACTION_PSP_COMPANY_NAME))
                    .address(getOrThrow(info, "address", TemplateDataField.TRANSACTION_PSP_ADDRESS))
                    .city(getOrThrow(info, "city", TemplateDataField.TRANSACTION_PSP_CITY))
                    .province(getOrThrow(info, "province", TemplateDataField.TRANSACTION_PSP_PROVINCE))
                    .buildingNumber(getOrThrow(info, "buildingNumber", TemplateDataField.TRANSACTION_PSP_BUILDING_NUMBER))
                    .postalCode(getOrThrow(info, "postalCode", TemplateDataField.TRANSACTION_PSP_POSTAL_CODE))
                    .logo(pspFee != null ? getOrThrow(info, "logo", TemplateDataField.TRANSACTION_PSP_LOGO) : info.get("logo"))
                    .build();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.TRANSACTION_PSP, TemplateDatasource.PSP_STATIC_CONFIG), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getOrThrow(
            LinkedHashMap<String, String> map,
            String key,
            String errorKey
    ) throws TemplateDataMappingException {
        String value = map.get(key);
        if (value == null) {
            throw new TemplateDataMappingException(formatErrorMessage(errorKey, TemplateDatasource.PSP_STATIC_CONFIG), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
        }
        return value;
    }

    private boolean getProcessedByPagoPA(BizEvent event) {
        if (event.getTransactionDetails() != null) {
            if (event.getTransactionDetails().getTransaction() != null &&
                    event.getTransactionDetails().getTransaction().getOrigin() != null) {
                return true;
            }
            if (event.getTransactionDetails().getInfo() != null &&
                    event.getTransactionDetails().getInfo().getClientId() != null) {
                return true;
            }
        }
        return false;
    }

    private String currencyFormat(String value) {
        BigDecimal valueToFormat = new BigDecimal(value);
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ITALY);
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setMinimumFractionDigits(2);
        return numberFormat.format(valueToFormat);
    }

    private String dateFormatZoned(String date) throws TemplateDataMappingException {
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ofPattern(RECEIPT_DATE_FORMAT))
                .toFormatter(Locale.ITALY)
                .withZone(TimeZone.getTimeZone("Europe/Rome").toZoneId());
        try {
            return OffsetDateTime.parse(date).format(formatter);
        } catch (DateTimeException e) {
            String errMsg = String.format("Error mapping bizEvent data to template, parse failed for property %s", TemplateDataField.TRANSACTION_TIMESTAMP);
            throw new TemplateDataMappingException(errMsg, ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e);
        }
    }

    private String dateFormat(String date) throws TemplateDataMappingException {
        DateTimeFormatter simpleDateFormat = DateTimeFormatter.ofPattern(RECEIPT_DATE_FORMAT).withLocale(Locale.ITALY);
        try {
            return LocalDateTime.parse(date).format(simpleDateFormat);
        } catch (DateTimeException e) {
            String errMsg = String.format("Error mapping bizEvent data to template, parse failed for property %s", TemplateDataField.TRANSACTION_TIMESTAMP);
            throw new TemplateDataMappingException(errMsg, ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e);
        }
    }

    private String formatErrorMessage(String missingProperty, TemplateDatasource dataSource) {
        return String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, dataSource.getName(), missingProperty);
    }

    private String formatFullName(String fullName, String fiscalCode) {
        if (fullName == null || fullName.equals(fiscalCode)) {
            return null;
        }

        Pattern pattern = Pattern.compile("^[\\d\\s\\W_]+$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(fullName);
        if (matcher.find()) {
            return null;
        }

        return fullName.replaceAll("[,;:/]+", " ");
    }

}

