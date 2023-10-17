package it.gov.pagopa.receipt.pdf.generator.exception;


public class PdfJsonMappingException extends RuntimeException {
    public PdfJsonMappingException(Exception e) {
        super(e);
    }
}
