package it.gov.pagopa.receipt.pdf.generator.utils;

public class TemplateDataField {
    public static final String ERROR_MAPPING_MESSAGE = "Error mapping bizEvent data to template, missing property %s";
    public static final String TRANSACTION_ID = "transaction.id";
    public static final String TRANSACTION_TIMESTAMP = "transaction.timestamp";
    public static final String TRANSACTION_AMOUNT = "transaction.amount";
    public static final String TRANSACTION_RRN = "transaction.rrn";
    public static final String USER_DATA_FULL_NAME = "user.data.fullName";
    public static final String USER_DATA_TAX_CODE = "user.data.taxCode";
    public static final String CART_ITEM_REF_NUMBER_TYPE = "cart.item.refNumber.type";
    public static final String CART_ITEM_REF_NUMBER_VALUE = "cart.item.refNumber.value";
    public static final String CART_ITEM_DEBTOR_TAX_CODE = "cart.item.debtor.taxCode";
    public static final String CART_ITEM_PAYEE_TAX_CODE = "cart.item.payee.taxCode";
    public static final String CART_ITEM_SUBJECT = "cart.item.subject";
    public static final String CART_ITEM_AMOUNT = "cart.item.amount";
    public static final String TRANSACTION_PSP_NAME = "transaction.psp.name";
    public static final String TRANSACTION_PSP = "transaction.psp";
    public static final String TRANSACTION_PSP_COMPANY_NAME = "transaction.psp.companyName";
    public static final String TRANSACTION_PSP_ADDRESS = "transaction.psp.address";
    public static final String TRANSACTION_PSP_CITY = "transaction.psp.city";
    public static final String TRANSACTION_PSP_PROVINCE = "transaction.psp.province";
    public static final String TRANSACTION_PSP_BUILDING_NUMBER = "transaction.psp.buildingNumber";
    public static final String TRANSACTION_PSP_POSTAL_CODE = "transaction.psp.postalCode";
    public static final String TRANSACTION_PSP_LOGO = "transaction.psp.logo";

    private TemplateDataField(){}
}
