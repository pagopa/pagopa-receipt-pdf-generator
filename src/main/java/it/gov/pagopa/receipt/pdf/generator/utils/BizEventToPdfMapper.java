package it.gov.pagopa.receipt.pdf.generator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;

import java.util.Map;

public class BizEventToPdfMapper {

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
                        event.getTransactionDetails().getTransaction() != null
        ) {
            return String.valueOf(event.getTransactionDetails().getTransaction().getIdTransaction());
        }
        if (
                event.getPaymentInfo() != null
        ) {
            return event.getPaymentInfo().getPaymentToken();
        }

        //TODO IUR?
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

        return event.getPaymentInfo() != null ? event.getPaymentInfo().getPaymentDateTime() : "";
    }

    public static String getAmount(BizEvent event){
        if(
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null
        ){
            return String.valueOf(event.getTransactionDetails().getTransaction().getAmount());
        }

        return event.getPaymentInfo() != null ? event.getPaymentInfo().getAmount() : "";
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
                        event.getTransactionDetails().getTransaction() != null
        ){
            return String.valueOf(event.getTransactionDetails().getTransaction().getFee());
        }

        return event.getPaymentInfo() != null ? event.getPaymentInfo().getFee() : null;
    }

    public static String getRnn(BizEvent event){
        if(
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getRrn() != null
        ){
            return event.getTransactionDetails().getTransaction().getRrn();
        }

        //TODO ECOMMERCE ANALYSIS
        //TODO NODO -> paymentToken / iur?
        return null;
    }

    public static String getAuthCode(BizEvent event){
        if(
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getTransaction() != null &&
                        event.getTransactionDetails().getTransaction().getAuthorizationCode() != null
        ){
            return event.getTransactionDetails().getTransaction().getAuthorizationCode();
        }

        return null;
    }

    public static String getPaymentMethodName(BizEvent event){
        if(
                event.getTransactionDetails() != null &&
                        event.getTransactionDetails().getWallet() != null &&
                        event.getTransactionDetails().getWallet().getInfo() != null &&
                        event.getTransactionDetails().getWallet().getInfo().getBrand() != null
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

    public static boolean getExtraFee(BizEvent event){
        //TODO Mapping ?
        return false;
    }

    public static String getUserMail(BizEvent event){
        //TODO Mapping ?
        return null;
    }

    public static String getUserFullName(BizEvent event){
        return event.getPayer() != null ? event.getPayer().getFullName() : null;
    }

    public static String getUserTaxCode(BizEvent event){
        return event.getPayer() != null ? event.getPayer().getEntityUniqueIdentifierValue() : null;
    }

    private static final String REF_NUMBER_TYPE = "CODICE AVVISO";
    public static String getRefNumberType(){
        return REF_NUMBER_TYPE;
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
        return event.getPaymentInfo() != null ? event.getPaymentInfo().getAmount() : null;
    }

}
