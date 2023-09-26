package it.gov.pagopa.receipt.pdf.generator.exception;

/** Thrown in case an error occurred when generating or saving a PDF Receipt */
public class PDFReceiptGenerationException extends Exception {

    private final int statusCode;

    /**
     * Constructs new exception with provided message, status code and cause
     *
     * @param message Detail message
     * @param statusCode Error code
     * @param cause Exception thrown
     */
    public PDFReceiptGenerationException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * Constructs new exception with provided message, status code
     *
     * @param message Detail message
     * @param statusCode Error code
     */
    public PDFReceiptGenerationException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
