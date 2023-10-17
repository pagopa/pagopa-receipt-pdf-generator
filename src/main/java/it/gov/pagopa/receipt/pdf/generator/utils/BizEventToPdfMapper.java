package it.gov.pagopa.receipt.pdf.generator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.exception.PdfJsonMappingException;
import it.gov.pagopa.receipt.pdf.generator.model.template.PSP;
import it.gov.pagopa.receipt.pdf.generator.model.template.PSPFee;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class BizEventToPdfMapper {

    private static final String REF_TYPE_NOTICE = "codiceAvviso";
    private static final String REF_TYPE_IUV = "IUV";

    /**
     * Hide from public usage.
     */

    private static final Map<String, String> brandLogoMap;
    private static final Map<String, Object> pspMap;

    static {
        try {
            brandLogoMap = ObjectMapperUtils.mapString(System.getenv().get("BRAND_LOGO_MAP"), Map.class);
        } catch (JsonProcessingException e) {
            throw new PdfJsonMappingException(e);
        }

    }

    static {
        try (InputStream data = BizEventToPdfMapper.class.getResourceAsStream("psp_config_file.json")) {
            if (data == null) {
                throw new IOException("PSP config file not found");
            }
            pspMap = ObjectMapperUtils.mapString(new String(data.readAllBytes()), Map.class);
        } catch (IOException e) {
            throw new PdfJsonMappingException(e);
        }
    }

    private BizEventToPdfMapper() {
    }

    public static String getId(BizEvent event) {
        if (event.getTransactionDetails() != null &&
                event.getTransactionDetails().getTransaction() != null &&
                event.getTransactionDetails().getTransaction().getIdTransaction() != 0L) {
            return String.valueOf(event.getTransactionDetails().getTransaction().getIdTransaction());
        }
        if (event.getPaymentInfo() != null) {
            return event.getPaymentInfo().getPaymentToken() != null ? event.getPaymentInfo().getPaymentToken()
                    : event.getPaymentInfo().getIUR();
        }

        return null;
    }

    public static String getTimestamp(BizEvent event) {
        if (event.getTransactionDetails() != null &&
                event.getTransactionDetails().getTransaction() != null &&
                event.getTransactionDetails().getTransaction().getCreationDate() != null) {
            return dateFormat(event.getTransactionDetails().getTransaction().getCreationDate(), true);
        }

        return event.getPaymentInfo() != null ? dateFormat(event.getPaymentInfo().getPaymentDateTime(), false) : null;
    }

    public static String getAmount(BizEvent event) {
        if (event.getTransactionDetails() != null &&
                event.getTransactionDetails().getTransaction() != null &&
                event.getTransactionDetails().getTransaction().getAmount() != 0L) {
            // Amount in transactionDetails is defined in cents (es. 25500 not 255.00)
            return currencyFormat(String.valueOf(event.getTransactionDetails().getTransaction().getAmount() / 100.00));
        }

        return event.getPaymentInfo() != null ? currencyFormat(event.getPaymentInfo().getAmount()) : null;
    }

    public static String getPspFee(BizEvent event) {
        if (event.getTransactionDetails() != null &&
                event.getTransactionDetails().getTransaction() != null &&
                event.getTransactionDetails().getTransaction().getFee() != 0L) {
            // Fee in transactionDetails is defined in cents (es. 25500 not 255.00)
            return currencyFormat(String.valueOf(event.getTransactionDetails().getTransaction().getFee() / 100.00));
        }

        return event.getPaymentInfo() != null ? currencyFormat(event.getPaymentInfo().getFee()) : null;
    }

    public static String getRnn(BizEvent event) {
        if (event.getTransactionDetails() != null &&
                event.getTransactionDetails().getTransaction() != null &&
                event.getTransactionDetails().getTransaction().getRrn() != null) {
            return event.getTransactionDetails().getTransaction().getRrn();
        }

        if (event.getPaymentInfo() != null) {
            return event.getPaymentInfo().getPaymentToken() != null ? event.getPaymentInfo().getPaymentToken()
                    : event.getPaymentInfo().getIUR();
        }

        return null;
    }

    public static String getAuthCode(BizEvent event) {
        if (event.getTransactionDetails() != null &&
                event.getTransactionDetails().getTransaction() != null) {
            return event.getTransactionDetails().getTransaction().getAuthorizationCode();
        }

        return null;
    }

    public static String getPaymentMethodName(BizEvent event) {
        if (event.getTransactionDetails() != null &&
                event.getTransactionDetails().getWallet() != null &&
                event.getTransactionDetails().getWallet().getInfo() != null) {
            return event.getTransactionDetails().getWallet().getInfo().getBrand();
        }

        return null;
    }

    public static String getPaymentMethodLogo(BizEvent event) {
        // TODO analyse -> transactionDetails.wallet.info.brandLogo doesn't exist

        if (event.getTransactionDetails() != null &&
                event.getTransactionDetails().getWallet() != null &&
                event.getTransactionDetails().getWallet().getInfo() != null) {
            return brandLogoMap.getOrDefault(
                    event.getTransactionDetails().getWallet()
                            .getInfo().getBrand(),
                    null);
        }

        return null;
    }

    public static String getPaymentMethodAccountHolder(BizEvent event) {
        if (event.getTransactionDetails() != null &&
                event.getTransactionDetails().getWallet() != null &&
                event.getTransactionDetails().getWallet().getInfo() != null &&
                event.getTransactionDetails().getWallet().getInfo().getHolder() != null) {
            return event.getTransactionDetails().getWallet().getInfo().getHolder();
        }

        return event.getPayer() != null ? event.getPayer().getFullName() : null;
    }

    public static String getUserMail() {
        return null;
    }

    public static String getUserFullName(BizEvent event) {
        return event.getPayer() != null ? event.getPayer().getFullName() : null;
    }

    public static String getUserTaxCode(BizEvent event) {
        return event.getPayer() != null ? event.getPayer().getEntityUniqueIdentifierValue() : null;
    }

    public static String getRefNumberType(BizEvent event) {
        if (event.getDebtorPosition() != null &&
                event.getDebtorPosition().getModelType() != null &&
                event.getDebtorPosition().getModelType().equals("2")) {
            return REF_TYPE_IUV;
        }

        return REF_TYPE_NOTICE;
    }

    public static String getRefNumberValue(BizEvent event) {
        return event.getDebtorPosition() != null ? event.getDebtorPosition().getIuv() : null;
    }

    public static String getDebtorFullName(BizEvent event) {
        return event.getDebtor() != null ? event.getDebtor().getFullName() : null;
    }

    public static String getDebtorTaxCode(BizEvent event) {
        return event.getDebtor() != null ? event.getDebtor().getEntityUniqueIdentifierValue() : null;
    }

    public static String getPayeeName(BizEvent event) {
        return event.getCreditor() != null ? event.getCreditor().getOfficeName() : null;
    }

    public static String getPayeeTaxCode(BizEvent event) {
        return event.getCreditor() != null ? event.getCreditor().getCompanyName() : null;
    }

    public static String getItemSubject(BizEvent event) {
        return event.getPaymentInfo() != null ? event.getPaymentInfo().getRemittanceInformation() : null;
    }

    public static String getItemAmount(BizEvent event) {
        return event.getPaymentInfo() != null ? currencyFormat(event.getPaymentInfo().getAmount()) : null;
    }

    private static String currencyFormat(String value) {
        BigDecimal valueToFormat = new BigDecimal(value);
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ITALY);

        return numberFormat.format(valueToFormat);
    }

    public static PSP getPsp(BizEvent event) {

        if (event.getPsp() != null &&
                event.getPsp().getIdPsp() != null &&
                event.getTransactionDetails() != null &&
                event.getTransactionDetails().getTransaction() != null &&
                event.getTransactionDetails().getTransaction().getPsp() != null) {
            String name = event.getTransactionDetails().getTransaction().getPsp().getBusinessName();
            LinkedHashMap<String, String> info = (LinkedHashMap<String, String>) pspMap
                    .getOrDefault(event.getPsp().getIdPsp(), new LinkedHashMap<>());
            return PSP.builder()
                    .name(name)
                    .fee(PSPFee.builder()
                            .amount(getPspFee(event))
                            .build())
                    .companyName(info.get("companyName"))
                    .address(info.get("address"))
                    .city(info.get("city"))
                    .province(info.get("province"))
                    .buildingNumber(info.get("buildingNumber"))
                    .postalCode(info.get("postalCode"))
                    .logo(info.get("logo"))
                    .build();

        }

        return null;
    }

    private static String dateFormat(String date, boolean withTimeZone){
        DateTimeFormatter simpleDateFormat = DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm:ss").withLocale(Locale.ITALY);

        if (withTimeZone) {
            return ZonedDateTime.parse(date).format(simpleDateFormat);
        }

        return LocalDateTime.parse(date).format(simpleDateFormat);
    }

}
