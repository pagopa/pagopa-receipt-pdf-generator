package it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration;

public enum ReasonErrorCode {
    ERROR_BLOB_STORAGE(901),
    ERROR_PDF_ENGINE(700),
    ERROR_TEMPLATE_PDF(903);

    private final int code;

    ReasonErrorCode(int code){
        this.code = code;
    }

    public int getCode(){
        return this.code;
    }
}
