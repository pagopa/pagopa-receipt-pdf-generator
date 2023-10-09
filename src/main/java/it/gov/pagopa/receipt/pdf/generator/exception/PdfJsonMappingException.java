package it.gov.pagopa.receipt.pdf.generator.exception;

import com.fasterxml.jackson.core.JsonProcessingException;

public class PdfJsonMappingException extends RuntimeException {
    public PdfJsonMappingException(JsonProcessingException e) {
    }
}
