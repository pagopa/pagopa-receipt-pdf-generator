package it.gov.pagopa.receipt.pdf.generator.exception;

/**
 * Thrown in case the PDF Receipt generation fail with an error that is useless to be retried.
 * Next generation will produce the same error.
 */
public class ReceiptGenerationNotToRetryException extends Exception {

    /**
     * Constructs new exception with provided message
     *
     * @param message Detail message
     */
    public ReceiptGenerationNotToRetryException(String message) {
        super(message);
    }
}
