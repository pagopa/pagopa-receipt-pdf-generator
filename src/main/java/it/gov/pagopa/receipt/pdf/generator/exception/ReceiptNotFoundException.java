package it.gov.pagopa.receipt.pdf.generator.exception;

/** Thrown in case no receipt is found in the CosmosDB container */
public class ReceiptNotFoundException extends Exception{

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public ReceiptNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause Exception thrown
     */
    public ReceiptNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}


