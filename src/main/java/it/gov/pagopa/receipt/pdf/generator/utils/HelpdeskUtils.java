package it.gov.pagopa.receipt.pdf.generator.utils;

import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.event.Transfer;
import it.gov.pagopa.receipt.pdf.generator.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.event.enumeration.UserType;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import lombok.Builder;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HelpdeskUtils {

    private static final String REMITTANCE_INFORMATION_REGEX = "/TXT/(.*)";
    private static final Boolean ECOMMERCE_FILTER_ENABLED = Boolean.parseBoolean(System.getenv().getOrDefault(
            "ECOMMERCE_FILTER_ENABLED", "true"));
    private static final List<String> AUTHENTICATED_CHANNELS = Arrays.asList(System.getenv().getOrDefault(
            "AUTHENTICATED_CHANNELS", "IO,CHECKOUT,WISP,CHECKOUT_CART").split(","));
    private static final List<String> UNWANTED_REMITTANCE_INFO = Arrays.asList(System.getenv().getOrDefault(
            "UNWANTED_REMITTANCE_INFO", "pagamento multibeneficiario,pagamento bpay").split(","));
    private static final List<String> ECOMMERCE = Arrays.asList("CHECKOUT", "CHECKOUT_CART");

    private HelpdeskUtils() {
    }

    /**
     * Checks if the instance of Biz Event is in status DONE and contains all the required information to process
     * in the receipt generation
     *
     * @param bizEvent BizEvent to validate
     * @return boolean to determine if the proposed event is invalid
     */
    public static BizEventValidityCheck isBizEventInvalid(BizEvent bizEvent) {

        if (bizEvent == null) {
            return BizEventValidityCheck.builder()
                    .invalid(true)
                    .error("Biz event is null")
                    .build();
        }

        if (!BizEventStatusType.DONE.equals(bizEvent.getEventStatus())) {
            return BizEventValidityCheck.builder()
                    .invalid(true)
                    .error(String.format("Biz event is in invalid status %s", bizEvent.getEventStatus()))
                    .build();
        }

        if (!hasValidFiscalCode(bizEvent)) {
            return BizEventValidityCheck.builder()
                    .invalid(true)
                    .error("Biz event is in invalid because debtor's and payer's identifiers are missing or not valid")
                    .build();
        }

        if (Boolean.TRUE.equals(ECOMMERCE_FILTER_ENABLED)
                && bizEvent.getTransactionDetails() != null
                && bizEvent.getTransactionDetails().getInfo() != null
                && ECOMMERCE.contains(bizEvent.getTransactionDetails().getInfo().getClientId())
        ) {
            return BizEventValidityCheck.builder()
                    .invalid(true)
                    .error("Biz event is in invalid because it is from e-commerce and e-commerce filter is enabled")
                    .build();
        }

        if (!isCartMod1(bizEvent)) {
            return BizEventValidityCheck.builder()
                    .invalid(true)
                    .error("Biz event is in invalid because contain either an invalid amount value or it is a legacy cart element")
                    .build();
        }

        return new BizEventValidityCheck(false, null);
    }

    @Builder
    public record BizEventValidityCheck(boolean invalid, String error) {
    }

    private static boolean hasValidFiscalCode(BizEvent bizEvent) {
        boolean isValidDebtor = false;
        boolean isValidPayer = false;

        if (bizEvent.getDebtor() != null && isValidFiscalCode(bizEvent.getDebtor().getEntityUniqueIdentifierValue())) {
            isValidDebtor = true;
        }
        if (isValidChannelOrigin(bizEvent)) {
            if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getUser() != null && isValidFiscalCode(bizEvent.getTransactionDetails().getUser().getFiscalCode())) {
                isValidPayer = true;
            }
            if (bizEvent.getPayer() != null && isValidFiscalCode(bizEvent.getPayer().getEntityUniqueIdentifierValue())) {
                isValidPayer = true;
            }
        }
        return isValidDebtor || isValidPayer;
    }

    public static Integer getTotalNotice(BizEvent bizEvent, ExecutionContext context, Logger logger) {
        if (bizEvent.getPaymentInfo() != null) {
            String totalNotice = bizEvent.getPaymentInfo().getTotalNotice();

            if (totalNotice != null) {
                int intTotalNotice;

                try {
                    intTotalNotice = Integer.parseInt(totalNotice);
                } catch (NumberFormatException e) {
                    logger.error("[{}] event with id {} discarded because has an invalid total notice value: {}",
                            context.getFunctionName(), bizEvent.getId(),
                            totalNotice);
                    throw e;
                }
                return intTotalNotice;
            }
        }
        return 1;
    }

    /**
     * Retrieve RemittanceInformation from BizEvent
     *
     * @param bizEvent BizEvent from which retrieve the data
     * @return the remittance information
     */
    public static String getItemSubject(BizEvent bizEvent) {
        if (
                bizEvent.getPaymentInfo() != null &&
                        bizEvent.getPaymentInfo().getRemittanceInformation() != null &&
                        !UNWANTED_REMITTANCE_INFO.contains(bizEvent.getPaymentInfo().getRemittanceInformation().toLowerCase())
        ) {
            return bizEvent.getPaymentInfo().getRemittanceInformation();
        }
        List<Transfer> transferList = bizEvent.getTransferList();
        if (transferList != null && !transferList.isEmpty()) {
            double amount = 0;
            String remittanceInformation = null;
            for (Transfer transfer : transferList) {
                double transferAmount;
                try {
                    transferAmount = Double.parseDouble(transfer.getAmount());
                } catch (Exception ignored) {
                    continue;
                }
                if (amount < transferAmount) {
                    amount = transferAmount;
                    remittanceInformation = transfer.getRemittanceInformation();
                }
            }
            return formatRemittanceInformation(remittanceInformation);
        }
        return null;
    }

    public static BigDecimal getAmount(BizEvent bizEvent) {
        if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null) {
            return formatEuroCentAmount(bizEvent.getTransactionDetails().getTransaction().getGrandTotal());
        }
        if (bizEvent.getPaymentInfo() != null && bizEvent.getPaymentInfo().getAmount() != null) {
            return new BigDecimal(bizEvent.getPaymentInfo().getAmount());
        }
        return BigDecimal.ZERO;
    }

    public static BigDecimal getCartAmount(BizEvent bizEvent) {
        if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null) {
            return formatEuroCentAmount(bizEvent.getTransactionDetails().getTransaction().getGrandTotal());
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal formatEuroCentAmount(long grandTotal) {
        BigDecimal amount = new BigDecimal(grandTotal);
        BigDecimal divider = new BigDecimal(100);
        return amount.divide(divider, 2, RoundingMode.UNNECESSARY);
    }

    public static String formatAmount(String value) {
        BigDecimal valueToFormat = new BigDecimal(value);
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ITALY);
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setMinimumFractionDigits(2);
        return numberFormat.format(valueToFormat);
    }

    private static String formatRemittanceInformation(String remittanceInformation) {
        if (remittanceInformation != null) {
            Pattern pattern = Pattern.compile(REMITTANCE_INFORMATION_REGEX);
            Matcher matcher = pattern.matcher(remittanceInformation);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return remittanceInformation;
    }

    public static boolean isReceiptStatusValid(Receipt receipt) {
        return receipt.getStatus() != ReceiptStatusType.FAILED && receipt.getStatus() != ReceiptStatusType.NOT_QUEUE_SENT;
    }

    public static boolean isCartStatusValid(CartForReceipt cartForReceipt) {
        return cartForReceipt.getStatus() != CartStatusType.FAILED && cartForReceipt.getStatus() != CartStatusType.NOT_QUEUE_SENT;
    }

    public static boolean isValidFiscalCode(String fiscalCode) {
        if (fiscalCode != null && !fiscalCode.isEmpty()) {
            Pattern patternCF = Pattern.compile("^[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$");
            Pattern patternPIVA = Pattern.compile("^\\d{11}$");

            return patternCF.matcher(fiscalCode).find() || patternPIVA.matcher(fiscalCode).find();
        }

        return false;
    }

    /**
     * Method to check if the content comes from a legacy cart model (see https://pagopa.atlassian.net/browse/VAS-1167)
     *
     * @param bizEvent bizEvent to validate
     * @return flag to determine if it is a manageable cart, or otherwise, will return false if
     * it is considered a legacy cart content (not having a totalNotice field and having amount values != 0)
     */
    public static boolean isCartMod1(BizEvent bizEvent) {
        if (bizEvent.getPaymentInfo() != null && bizEvent.getPaymentInfo().getTotalNotice() == null) {
            return bizEvent.getTransactionDetails() != null &&
                    new BigDecimal(bizEvent.getPaymentInfo().getAmount()).subtract(
                                    formatEuroCentAmount(bizEvent.getTransactionDetails().getTransaction().getAmount()))
                            .floatValue() == 0;
        }
        return true;
    }

    public static boolean isValidChannelOrigin(BizEvent bizEvent) {
        var details = bizEvent.getTransactionDetails();
        if (details == null) {
            return false;
        }

        String origin = details.getTransaction() != null
                ? details.getTransaction().getOrigin()
                : null;

        String clientId = details.getInfo() != null
                ? details.getInfo().getClientId()
                : null;

        UserType userType = details.getUser() != null
                ? details.getUser().getType()
                : null;

        boolean isAuthenticated = AUTHENTICATED_CHANNELS.contains(origin) || AUTHENTICATED_CHANNELS.contains(clientId);
        boolean isCheckout = ECOMMERCE.contains(origin) || ECOMMERCE.contains(clientId);
        boolean isRegisteredUser = UserType.REGISTERED.equals(userType);

        if (isCheckout && !isRegisteredUser) {
            return false;
        }

        return isAuthenticated;
    }

    public static String getTransactionCreationDate(BizEvent bizEvent) {
        if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null) {
            return bizEvent.getTransactionDetails().getTransaction().getCreationDate();

        } else if (bizEvent.getPaymentInfo() != null) {
            return bizEvent.getPaymentInfo().getPaymentDateTime();
        }

        return null;
    }
}
