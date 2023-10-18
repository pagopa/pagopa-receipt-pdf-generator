package it.gov.pagopa.receipt.pdf.generator.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.exception.PdfJsonMappingException;
import it.gov.pagopa.receipt.pdf.generator.exception.TemplateDataMappingException;
import it.gov.pagopa.receipt.pdf.generator.model.template.*;
import it.gov.pagopa.receipt.pdf.generator.service.BuildTemplateService;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import it.gov.pagopa.receipt.pdf.generator.utils.TemplateDataField;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class BuildTemplateServiceImpl implements BuildTemplateService {


    private static final String REF_TYPE_NOTICE = "codiceAvviso";
    private static final String REF_TYPE_IUV = "IUV";

    private static final String BRAND_LOGO_MAP_ENV_KEY = "BRAND_LOGO_MAP";
    private static final String PSP_CONFIG_FILE_JSON_FILE_NAME = "psp_config_file.json";

    /**
     * Hide from public usage.
     */

    private static final Map<String, String> brandLogoMap;
    private static final Map<String, Object> pspMap;

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
    public ReceiptPDFTemplate buildTemplate(BizEvent bizEvent, boolean partialTemplate) throws TemplateDataMappingException {
        return ReceiptPDFTemplate.builder()
                .transaction(Transaction.builder()
                        .id(getId(bizEvent))
                        .timestamp(getTimestamp(bizEvent))
                        .amount(getAmount(bizEvent))
                        .psp(getPsp(bizEvent))
                        .rrn(getRnn(bizEvent))
                        .paymentMethod(PaymentMethod.builder()
                                .name(getPaymentMethodName(bizEvent))
                                .logo(getPaymentMethodLogo(bizEvent))
                                .accountHolder(getPaymentMethodAccountHolder(bizEvent))
                                .build())
                        .authCode(getAuthCode(bizEvent))
                        .requestedByDebtor(partialTemplate)
                        .build())
                .user(partialTemplate ?
                        null :
                        User.builder()
                                .data(UserData.builder()
                                        .fullName(getUserFullName(bizEvent))
                                        .taxCode(getUserTaxCode(bizEvent))
                                        .build())
                                .build())
                .cart(Cart.builder()
                        .items(Collections.singletonList(
                                Item.builder()
                                        .refNumber(RefNumber.builder()
                                                .type(getRefNumberType(bizEvent))
                                                .value(getRefNumberValue(bizEvent))
                                                .build())
                                        .debtor(Debtor.builder()
                                                .fullName(getDebtorFullName(bizEvent))
                                                .taxCode(getDebtorTaxCode(bizEvent))
                                                .build())
                                        .payee(Payee.builder()
                                                .name(getPayeeName(bizEvent))
                                                .taxCode(getPayeeTaxCode(bizEvent))
                                                .build())
                                        .subject(getItemSubject(bizEvent))
                                        .amount(getItemAmount(bizEvent))
                                        .build()
                        ))
                        //Cart items total amount w/o fee, TODO change it with multiple cart items implementation
                        .amountPartial(getItemAmount(bizEvent))
                        .build())
                .build();
    }

    private String getId(BizEvent event) throws TemplateDataMappingException {
        if (
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getIdTransaction() != 0L
        ) {
            return String.valueOf(event.getTransactionDetails().getTransaction().getIdTransaction());
        }
        if (event.getPaymentInfo() != null) {
            if (event.getPaymentInfo().getPaymentToken() != null) {
                return event.getPaymentInfo().getPaymentToken();
            }
            if (event.getPaymentInfo().getIUR() != null) {
                return event.getPaymentInfo().getIUR();
            }
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.TRANSACTION_ID), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getTimestamp(BizEvent event) throws TemplateDataMappingException {
        if (
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getCreationDate() != null
        ) {
            return dateFormat(event.getTransactionDetails().getTransaction().getCreationDate(), true);
        }
        if (event.getPaymentInfo() != null && event.getPaymentInfo().getPaymentDateTime() != null) {
            return dateFormat(event.getPaymentInfo().getPaymentDateTime(), false);
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.TRANSACTION_TIMESTAMP), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getAmount(BizEvent event) throws TemplateDataMappingException {
        if (
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getGrandTotal() != 0L
        ) {
            // Amount in transactionDetails is defined in cents (es. 25500 not 255.00)
            return currencyFormat(String.valueOf(event.getTransactionDetails().getTransaction().getGrandTotal() / 100.00));
        }
        if (event.getPaymentInfo() != null && event.getPaymentInfo().getAmount() != null) {
            return currencyFormat(event.getPaymentInfo().getAmount());
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.TRANSACTION_AMOUNT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
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
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.TRANSACTION_RRN), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getAuthCode(BizEvent event) {
        if (event.getTransactionDetails() != null && event.getTransactionDetails().getTransaction() != null) {
            return event.getTransactionDetails().getTransaction().getAuthorizationCode();
        }
        return null;
    }

    private String getPaymentMethodName(BizEvent event) {
        if (
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getWallet() != null &&
                        event.getTransactionDetails().getWallet().getInfo() != null &&
                        event.getTransactionDetails().getWallet().getInfo().getBrand() != null
        ) {
            return event.getTransactionDetails().getWallet().getInfo().getBrand();
        }
        return null;
    }

    private String getPaymentMethodLogo(BizEvent event) {
        return brandLogoMap.getOrDefault(getPaymentMethodName(event), null);
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
        return event.getPayer() != null ? event.getPayer().getFullName() : null;
    }

    private String getUserFullName(BizEvent event) throws TemplateDataMappingException {
        if (event.getPayer() != null && event.getPayer().getFullName() != null) {
            return event.getPayer().getFullName();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.USER_DATA_FULL_NAME), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getUserTaxCode(BizEvent event) throws TemplateDataMappingException {
        if (event.getPayer() != null && event.getPayer().getEntityUniqueIdentifierValue() != null) {
            return event.getPayer().getEntityUniqueIdentifierValue();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.USER_DATA_TAX_CODE), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getRefNumberType(BizEvent event) throws TemplateDataMappingException {
        if (event.getDebtorPosition() != null && event.getDebtorPosition().getModelType() != null) {
            if (event.getDebtorPosition().getModelType().equals("1")) {
                return REF_TYPE_NOTICE;
            }
            if (event.getDebtorPosition().getModelType().equals("2")) {
                return REF_TYPE_IUV;
            }
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.CART_ITEM_REF_NUMBER_TYPE), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getRefNumberValue(BizEvent event) throws TemplateDataMappingException {
        if (event.getDebtorPosition() != null && event.getDebtorPosition().getIuv() != null) {
            return event.getDebtorPosition().getIuv();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.CART_ITEM_REF_NUMBER_VALUE), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getDebtorFullName(BizEvent event) {
        return event.getDebtor() != null ? event.getDebtor().getFullName() : null;
    }

    private String getDebtorTaxCode(BizEvent event) throws TemplateDataMappingException {
        if (event.getDebtor() != null && event.getDebtor().getEntityUniqueIdentifierValue() != null) {
            return event.getDebtor().getEntityUniqueIdentifierValue();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.CART_ITEM_DEBTOR_TAX_CODE), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getPayeeName(BizEvent event) {
        return event.getCreditor() != null ? event.getCreditor().getOfficeName() : null;
    }

    private String getPayeeTaxCode(BizEvent event) throws TemplateDataMappingException {
        if (event.getCreditor() != null && event.getCreditor().getCompanyName() != null) {
            return event.getCreditor().getCompanyName();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.CART_ITEM_PAYEE_TAX_CODE), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getItemSubject(BizEvent event) throws TemplateDataMappingException {
        if (event.getPaymentInfo() != null && event.getPaymentInfo().getRemittanceInformation() != null) {
            return event.getPaymentInfo().getRemittanceInformation();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.CART_ITEM_SUBJECT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getItemAmount(BizEvent event) throws TemplateDataMappingException {
        if (event.getPaymentInfo() != null && event.getPaymentInfo().getAmount() != null) {
            return currencyFormat(event.getPaymentInfo().getAmount());
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.CART_ITEM_AMOUNT), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
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

    private String getPspName(BizEvent event) throws TemplateDataMappingException {
        if (  event.getTransactionDetails() != null &&
                event.getTransactionDetails().getTransaction() != null &&
                event.getTransactionDetails().getTransaction().getPsp() != null &&
                event.getTransactionDetails().getTransaction().getPsp().getBusinessName() != null
        ) {
            return event.getTransactionDetails().getTransaction().getPsp().getBusinessName();
        }
        if (event.getPsp().getPsp() != null) {
            return event.getPsp().getPsp();
        }
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.TRANSACTION_PSP_NAME), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private PSP getPsp(BizEvent event) throws TemplateDataMappingException {
        if (event.getPsp() != null && event.getPsp().getIdPsp() != null) {
            LinkedHashMap<String, String> info = (LinkedHashMap<String, String>) pspMap
                    .getOrDefault(event.getPsp().getIdPsp(), new LinkedHashMap<>());
            String pspFee = getPspFee(event);
            return PSP.builder()
                    .name(getPspName(event))
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
        throw new TemplateDataMappingException(formatErrorMessage(TemplateDataField.TRANSACTION_PSP), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
    }

    private String getOrThrow(LinkedHashMap<String, String> map, String key, String errorKey) throws TemplateDataMappingException {
        String value = map.get(key);
        if (value == null) {
            throw new TemplateDataMappingException(formatErrorMessage(errorKey), ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode());
        }
        return value;
    }

    private String currencyFormat(String value) {
        BigDecimal valueToFormat = new BigDecimal(value);
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ITALY);
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setMinimumFractionDigits(2);
        return numberFormat.format(valueToFormat);
    }

    private String dateFormat(String date, boolean withTimeZone) {
        DateTimeFormatter simpleDateFormat = DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm:ss").withLocale(Locale.ITALY);
        if (withTimeZone) {
            return ZonedDateTime.parse(date).format(simpleDateFormat);
        }
        return LocalDateTime.parse(date).format(simpleDateFormat);
    }

    private String formatErrorMessage(String missingProperty) {
        return String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, missingProperty);
    }
}
