package it.gov.pagopa.receipt.pdf.generator.exception;

/** Thrown in case an error occurred when mapping the bizEvent to the template */
public class TemplateDataMappingException extends PDFReceiptGenerationException {

    /**
     * Constructs new exception with provided message, status code and cause
     *
     * @param message Detail message
     * @param statusCode Error code
     * @param cause Exception thrown
     */
    public TemplateDataMappingException(String message, int statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }

    /**
     * Constructs new exception with provided message, status code
     *
     * @param message Detail message
     * @param statusCode Error code
     */
    public TemplateDataMappingException(String message, int statusCode) {
        super(message, statusCode);
    }
}
