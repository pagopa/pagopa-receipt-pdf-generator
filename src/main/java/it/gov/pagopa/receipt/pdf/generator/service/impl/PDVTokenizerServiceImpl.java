package it.gov.pagopa.receipt.pdf.generator.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.generator.client.PDVTokenizerClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.PDVTokenizerClientImpl;
import it.gov.pagopa.receipt.pdf.generator.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.generator.model.tokenizer.ErrorResponse;
import it.gov.pagopa.receipt.pdf.generator.model.tokenizer.PiiResource;
import it.gov.pagopa.receipt.pdf.generator.model.tokenizer.TokenResource;
import it.gov.pagopa.receipt.pdf.generator.service.PDVTokenizerService;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import org.apache.http.HttpStatus;

import java.net.http.HttpResponse;

/**
 * {@inheritDoc}
 * Unused, needed when the BizEvent will be adequately tokenized
 */
public class PDVTokenizerServiceImpl implements PDVTokenizerService {

    private final PDVTokenizerClient pdvTokenizerClient;

    public PDVTokenizerServiceImpl() {
        this.pdvTokenizerClient = PDVTokenizerClientImpl.getInstance();
    }

    PDVTokenizerServiceImpl(PDVTokenizerClient pdvTokenizerClient) {
        this.pdvTokenizerClient = pdvTokenizerClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getToken(String fiscalCode) throws JsonProcessingException, PDVTokenizerException {
        PiiResource piiResource = PiiResource.builder().pii(fiscalCode).build();
        String tokenizerBody = ObjectMapperUtils.writeValueAsString(piiResource);

        HttpResponse<String> httpResponse = pdvTokenizerClient.searchTokenByPII(tokenizerBody);

        if (httpResponse.statusCode() != HttpStatus.SC_OK) {
            ErrorResponse errorResponse = ObjectMapperUtils.mapString(httpResponse.body(), ErrorResponse.class);
            String errMsg = String.format("PDV Tokenizer getToken invocation failed: %s. Error description: %s (%s)",
                    errorResponse.getTitle(), errorResponse.getDetail(), errorResponse.getType());
            throw new PDVTokenizerException(errMsg, errorResponse.getStatus());
        }
        TokenResource tokenResource = ObjectMapperUtils.mapString(httpResponse.body(), TokenResource.class);
        return tokenResource.getToken();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFiscalCode(String token) throws PDVTokenizerException, JsonProcessingException {
        HttpResponse<String> httpResponse = pdvTokenizerClient.findPIIByToken(token);

        if (httpResponse.statusCode() != HttpStatus.SC_OK) {
            ErrorResponse errorResponse = ObjectMapperUtils.mapString(httpResponse.body(), ErrorResponse.class);
            String errMsg = String.format("PDV Tokenizer getFiscalCode invocation failed: %s. Error description: %s (%s)",
                    errorResponse.getTitle(), errorResponse.getDetail(), errorResponse.getType());
            throw new PDVTokenizerException(errMsg, errorResponse.getStatus());
        }
        PiiResource piiResource = ObjectMapperUtils.mapString(httpResponse.body(), PiiResource.class);
        return piiResource.getPii();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateTokenForFiscalCode(String fiscalCode) throws PDVTokenizerException, JsonProcessingException {
        PiiResource piiResource = PiiResource.builder().pii(fiscalCode).build();
        String tokenizerBody = ObjectMapperUtils.writeValueAsString(piiResource);

        HttpResponse<String> httpResponse = pdvTokenizerClient.createToken(tokenizerBody);

        if (httpResponse.statusCode() != HttpStatus.SC_OK) {
            ErrorResponse errorResponse = ObjectMapperUtils.mapString(httpResponse.body(), ErrorResponse.class);
            String errMsg = String.format("PDV Tokenizer generateTokenForFiscalCode invocation failed: %s. Error description: %s (%s)",
                    errorResponse.getTitle(), errorResponse.getDetail(), errorResponse.getType());
            throw new PDVTokenizerException(errMsg, errorResponse.getStatus());
        }
        TokenResource tokenResource = ObjectMapperUtils.mapString(httpResponse.body(), TokenResource.class);
        return tokenResource.getToken();
    }
}
