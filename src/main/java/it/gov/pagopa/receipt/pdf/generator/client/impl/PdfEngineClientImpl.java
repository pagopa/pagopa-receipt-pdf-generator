package it.gov.pagopa.receipt.pdf.generator.client.impl;

import it.gov.pagopa.receipt.pdf.generator.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.generator.model.PdfEngineErrorResponse;
import it.gov.pagopa.receipt.pdf.generator.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.ZIP_FILE_NAME;

/**
 * Client for the PDF Engine
 */
@Slf4j
public class PdfEngineClientImpl implements PdfEngineClient {

    private static PdfEngineClientImpl instance = null;

    private final String pdfEngineEndpoint = System.getenv().getOrDefault("PDF_ENGINE_ENDPOINT", "");
    private final String ocpAimSubKey = System.getenv().getOrDefault("OCP_APIM_SUBSCRIPTION_KEY", "");

    private static final String HEADER_AUTH_KEY = "Ocp-Apim-Subscription-Key";
    private static final String TEMPLATE_KEY = "template";
    private static final String DATA_KEY = "data";

    private final CloseableHttpClient client;

    private PdfEngineClientImpl() {
        this.client = HttpClientBuilder.create().build();
    }

    PdfEngineClientImpl(CloseableHttpClient client) {
        this.client = client;
    }

    public static PdfEngineClientImpl getInstance() {
        if (instance == null) {
            instance = new PdfEngineClientImpl();
        }

        return instance;
    }

    /**
     * Generate the client, builds the request and returns the response
     *
     * @param pdfEngineRequest Request to the client
     * @return response with the PDF or error message and the status
     */
    @Override
    public PdfEngineResponse generatePDF(PdfEngineRequest pdfEngineRequest, Path workingDirPath) {
        //Generate client
        try (InputStream templateStream = pdfEngineRequest.getTemplate().openStream()) {
            //Encode template and data
            log.info("Create multipart request");
            HttpPost request = buildMultipartRequest(pdfEngineRequest, templateStream);
            log.info("Multipart request created");

            return makeCall(request, workingDirPath);
        } catch (IOException e) {
            return createErrorResponse(e);
        }
    }

    private HttpPost buildMultipartRequest(PdfEngineRequest pdfEngineRequest, InputStream templateStream) {
        StringBody dataBody = new StringBody(pdfEngineRequest.getData(), ContentType.APPLICATION_JSON);

        //Build the multipart request
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addBinaryBody(TEMPLATE_KEY, templateStream, ContentType.create("application/zip"), ZIP_FILE_NAME);
        builder.addPart(DATA_KEY, dataBody);
        HttpEntity entity = builder.build();

        //Set endpoint and auth key
        HttpPost request = new HttpPost(pdfEngineEndpoint);
        request.setHeader(HEADER_AUTH_KEY, ocpAimSubKey);
        request.setEntity(entity);
        return request;
    }

    /**
     * Calls the PDF Engine and handles its response, updating the PdfEngineResponse accordingly
     *
     * @param request The request to the PDF engine
     * @return pdf engine response
     */
    private PdfEngineResponse makeCall(HttpPost request, Path workingDirPath) {
        PdfEngineResponse pdfEngineResponse = new PdfEngineResponse();
        //Execute call
        try (CloseableHttpResponse response = this.client.execute(request)) {
            //Retrieve response
            int statusCode = response.getStatusLine().getStatusCode();
            log.info("PDF Engine Responded with {}", statusCode);
            HttpEntity entityResponse = response.getEntity();

            //Handles response
            if (statusCode == HttpStatus.SC_OK && entityResponse != null) {
                pdfEngineResponse = handleSuccessResponse(workingDirPath, entityResponse);
            } else {
                pdfEngineResponse = handleErrorResponse(response, entityResponse);
            }
            // ensure entity is fully consumed
            EntityUtils.consumeQuietly(entityResponse);
            return pdfEngineResponse;
        } catch (Exception e) {
            log.error("Error calling PDF Engine", e);
            return createErrorResponse(e);
        }
    }

    /**
     * Handle success response from PDF Engine by saving the generated PDF in a temp file
     *
     * @param workingDirPath the path where the temp file will be saved
     * @param entityResponse the response form PDF Engine
     * @return the response with the reference to the temp file
     * @throws IOException if an error occur while saving the temp file
     */
    private PdfEngineResponse handleSuccessResponse(
            Path workingDirPath,
            HttpEntity entityResponse
    ) throws IOException {
        try (InputStream inputStream = entityResponse.getContent()) {
            File targetFile = File.createTempFile("tempFile", ".pdf", workingDirPath.toFile());
            FileUtils.copyInputStreamToFile(inputStream, targetFile);

            PdfEngineResponse pdfEngineResponse = new PdfEngineResponse();
            pdfEngineResponse.setStatusCode(HttpStatus.SC_OK);
            pdfEngineResponse.setTempPdfPath(targetFile.getAbsolutePath());
            return pdfEngineResponse;
        }
    }

    /**
     * Handles error response from the PDF Engine
     *
     * @param response       Response from the PDF engine
     * @param entityResponse Response content from the PDF Engine
     * @throws IOException in case of error encoding to string
     */
    private PdfEngineResponse handleErrorResponse(
            CloseableHttpResponse response,
            HttpEntity entityResponse
    ) throws IOException {
        //Verify if unauthorized
        if (response != null &&
                response.getStatusLine() != null &&
                response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED
        ) {
            return createErrorResponse("Unauthorized call to PDF engine function");
        }

        String errMsg = extractErrorMessageFormBody(entityResponse);
        if (errMsg == null) {
            errMsg = "Unknown error in PDF engine function";
        }
        return createErrorResponse(errMsg);
    }

    private String extractErrorMessageFormBody(HttpEntity entityResponse) throws IOException {
        String errMsg = null;
        if (entityResponse != null) {
            //Handle JSON response
            String jsonString = EntityUtils.toString(entityResponse, StandardCharsets.UTF_8);

            if (!jsonString.isEmpty()) {
                PdfEngineErrorResponse errorResponse = ObjectMapperUtils.mapString(jsonString, PdfEngineErrorResponse.class);

                if (errorResponse != null &&
                        errorResponse.getErrors() != null &&
                        !errorResponse.getErrors().isEmpty() &&
                        errorResponse.getErrors().get(0) != null
                ) {
                    errMsg = errorResponse.getErrors().get(0).getMessage();
                }
            }
        }
        return errMsg;
    }

    private PdfEngineResponse createErrorResponse(Exception e) {
        PdfEngineResponse pdfEngineResponse = new PdfEngineResponse();
        pdfEngineResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        pdfEngineResponse.setErrorMessage(String.format("Exception thrown during pdf generation process: %s", e));
        return pdfEngineResponse;
    }

    private PdfEngineResponse createErrorResponse(String errMsg) {
        PdfEngineResponse pdfEngineResponse = new PdfEngineResponse();
        pdfEngineResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        pdfEngineResponse.setErrorMessage(errMsg);
        return pdfEngineResponse;
    }
}
