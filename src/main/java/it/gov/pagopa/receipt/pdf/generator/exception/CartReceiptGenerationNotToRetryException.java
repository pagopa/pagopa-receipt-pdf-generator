package it.gov.pagopa.receipt.pdf.generator.exception;

/**
 * Thrown in case the PDF Cart receipt generation fail with an error that is useless to be retried.
 * Next generation will produce the same error.
 */
public class CartReceiptGenerationNotToRetryException extends Exception {

    /**
     * Constructs new exception with provided message
     *
     * @param message Detail message
     */
    public CartReceiptGenerationNotToRetryException(String message) {
        super(message);
    }
}
