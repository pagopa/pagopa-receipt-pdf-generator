package it.gov.pagopa.receipt.pdf.generator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import java.util.Map;

public class BizEventToPdfMapper {

    private static final String REF_TYPE_NOTICE = "CODICE AVVISO";
    private static final String REF_TYPE_IUV = "IUV";

    /**
     * Hide from public usage.
     */

    private static final Map<String, String> brandLogoMap;

    static {
        try {
            brandLogoMap = ObjectMapperUtils.mapString(System.getenv().get("BRAND_LOGO_MAP"),Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private BizEventToPdfMapper() {
    }

    public static String getId(BizEvent event) {
        if (
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getIdTransaction() != 0L
        ) {
            return String.valueOf(event.getTransactionDetails().getTransaction().getIdTransaction());
        }
        if (
                event.getPaymentInfo() != null
        ) {
            return event.getPaymentInfo().getPaymentToken() != null ? event.getPaymentInfo().getPaymentToken() : event.getPaymentInfo().getIUR();
        }

        return null;
    }

    public static String getTimestamp(BizEvent event) {
        if(
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getCreationDate() != null
        ){
            return event.getTransactionDetails().getTransaction().getCreationDate();
        }

        return event.getPaymentInfo() != null ? event.getPaymentInfo().getPaymentDateTime() : null;
    }

    public static String getAmount(BizEvent event){
        if(
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getAmount() != 0L
        ){
            //Amount in transactionDetails is defined in cents (es. 25500 not 255.00)
            return valueFormat(String.valueOf(event.getTransactionDetails().getTransaction().getAmount() / 100.00));
        }

        return event.getPaymentInfo() != null ? valueFormat(event.getPaymentInfo().getAmount()) : null;
    }

    public static String getPspName(BizEvent event){
        if(
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getPsp() != null &&
                        event.getTransactionDetails().getTransaction().getPsp().getBusinessName() != null
        ){
            return event.getTransactionDetails().getTransaction().getPsp().getBusinessName();
        }

        return event.getPsp() != null ? event.getPsp().getPsp() : null;
    }

    public static String getPspFee(BizEvent event){
        if(
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getFee() != 0L
        ){
            //Fee in transactionDetails is defined in cents (es. 25500 not 255.00)
            return valueFormat(String.valueOf(event.getTransactionDetails().getTransaction().getFee() / 100.00));
        }

        return event.getPaymentInfo() != null ? valueFormat(event.getPaymentInfo().getFee()) : null;
    }

    public static String getRnn(BizEvent event){
        if(
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getRrn() != null
        ){
            return event.getTransactionDetails().getTransaction().getRrn();
        }

        if (
                event.getPaymentInfo() != null
        ) {
            return event.getPaymentInfo().getPaymentToken() != null ? event.getPaymentInfo().getPaymentToken() : event.getPaymentInfo().getIUR();
        }

        return null;
    }

    public static String getAuthCode(BizEvent event){
        if(
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null
        ){
            return event.getTransactionDetails().getTransaction().getAuthorizationCode();
        }

        return null;
    }

    public static String getPaymentMethodName(BizEvent event){
        if(
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getWallet() != null &&
                        event.getTransactionDetails().getWallet().getInfo() != null
        ){
            return event.getTransactionDetails().getWallet().getInfo().getBrand();
        }

        return null;
    }

    public static String getPaymentMethodLogo(BizEvent event){
        //TODO analyse -> transactionDetails.wallet.info.brandLogo doesn't exist

        if (event.getTransactionDetails() != null &&
            event.getTransactionDetails().getWallet() != null &&
            event.getTransactionDetails().getWallet().getInfo() != null
        ) {
            return brandLogoMap.getOrDefault(
                    event.getTransactionDetails().getWallet()
                            .getInfo().getBrand(),null
            );
        }

        return null;
    }

    public static String getPaymentMethodAccountHolder(BizEvent event){
        if(
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getWallet() != null &&
                        event.getTransactionDetails().getWallet().getInfo() != null &&
                        event.getTransactionDetails().getWallet().getInfo().getHolder() != null
        ){
            return event.getTransactionDetails().getWallet().getInfo().getHolder();
        }

        return event.getPayer() != null ? event.getPayer().getFullName() : null;
    }

    public static String getUserMail(){
        return null;
    }

    public static String getUserFullName(BizEvent event){
        return event.getPayer() != null ? event.getPayer().getFullName() : null;
    }

    public static String getUserTaxCode(BizEvent event){
        return event.getPayer() != null ? event.getPayer().getEntityUniqueIdentifierValue() : null;
    }


    public static String getRefNumberType(BizEvent event){
        if(
                event.getDebtorPosition() != null &&
                event.getDebtorPosition().getModelType().equals("2")
        ){
            return REF_TYPE_IUV;
        }

        return REF_TYPE_NOTICE;
    }

    public static String getRefNumberValue(BizEvent event){
        return event.getDebtorPosition() != null ? event.getDebtorPosition().getIuv() : null;
    }

    public static String getDebtorFullName(BizEvent event){
        return event.getDebtor() != null ? event.getDebtor().getFullName() : null;
    }

    public static String getDebtorTaxCode(BizEvent event){
        return event.getDebtor() != null ? event.getDebtor().getEntityUniqueIdentifierValue() : null;
    }

    public static String getPayeeName(BizEvent event){
        return event.getCreditor() != null ? event.getCreditor().getOfficeName() : null;
    }

    public static String getPayeeTaxCode(BizEvent event){
        return event.getCreditor() != null ? event.getCreditor().getCompanyName() : null;
    }

    public static String getItemSubject(BizEvent event){
        return event.getPaymentInfo() != null ? event.getPaymentInfo().getRemittanceInformation() : null;
    }

    public static String getItemAmount(BizEvent event){
        return event.getPaymentInfo() != null ? valueFormat(event.getPaymentInfo().getAmount()) : null;
    }

    private static String valueFormat(String value){
        BigDecimal valueToFormat = new BigDecimal(value);
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ITALY);

        return numberFormat.format(valueToFormat);
    }

}
