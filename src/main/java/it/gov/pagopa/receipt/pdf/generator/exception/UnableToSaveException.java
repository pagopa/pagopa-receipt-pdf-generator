package it.gov.pagopa.receipt.pdf.generator.exception;

public class UnableToSaveException extends Exception {

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public UnableToSaveException(String message) {
        super(message);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause Exception thrown
     */
    public UnableToSaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
