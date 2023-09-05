package it.gov.pagopa.receipt.pdf.generator.exception;

public class UnableToQueueException extends Exception {

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public UnableToQueueException(String message) {
        super(message);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause Exception thrown
     */
    public UnableToQueueException(String message, Throwable cause) {
        super(message, cause);
    }

}
