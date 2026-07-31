package it.gov.pagopa.receipt.pdf.generator.client.impl;

import it.gov.pagopa.receipt.pdf.generator.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.generator.model.PdfEngineErrorResponse;
import it.gov.pagopa.receipt.pdf.generator.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static it.gov.pagopa.receipt.pdf.generator.utils.Constants.ZIP_FILE_NAME;

/**
 * Client for the PDF Engine
 */
@Slf4j
public class PdfEngineClientImpl implements PdfEngineClient {

    private static final String HEADER_AUTH_KEY = "Ocp-Apim-Subscription-Key";
    private static final String TEMPLATE_KEY = "template";
    private static final String DATA_KEY = "data";

    private static final URL TEMPLATE_STREAM = PdfEngineClientImpl.class.getClassLoader().getResource(ZIP_FILE_NAME);

    private final String pdfEngineEndpoint = System.getenv().getOrDefault("PDF_ENGINE_ENDPOINT", "");
    private final Header subKeyHeader = new BasicHeader(
            HEADER_AUTH_KEY,
            System.getenv().getOrDefault("OCP_APIM_SUBSCRIPTION_KEY", ""));

    // ---------- HTTP timeouts (ms) ----------
    // Apache HttpClient defaults are -1 (infinite).
    /** Max time to establish TCP+TLS connection. */
    private static final int CONNECT_TIMEOUT_MS = envInt("PDF_ENGINE_HTTP_CONNECT_TIMEOUT_MS", 5_000);
    /** Max wait to lease a connection from the pool. Fail fast when pool is saturated. */
    private static final int CONNECTION_REQUEST_TIMEOUT_MS = envInt("PDF_ENGINE_HTTP_CONN_REQUEST_TIMEOUT_MS", 2_000);
    /** SO_TIMEOUT: max inactivity while reading the response. */
    private static final int SOCKET_TIMEOUT_MS = envInt("PDF_ENGINE_HTTP_SOCKET_TIMEOUT_MS", 30_000);
    /** Retries on transient I/O errors (see {@link #buildRetryHandler()}). */
    private static final int RETRY_COUNT = envInt("PDF_ENGINE_HTTP_RETRY_COUNT", 2);

    // ---------- Connection pool ----------
    // HttpClient defaults are maxTotal=20, maxPerRoute=2.
    /** Max concurrent connections to the PDF Engine route. Size on per-JVM concurrency peak. */
    private static final int MAX_CONN_PER_ROUTE = envInt("PDF_ENGINE_HTTP_MAX_CONN_PER_ROUTE", 50);
    /** Pool cap. Single route here, so aligned with {@link #MAX_CONN_PER_ROUTE}. */
    private static final int MAX_CONN_TOTAL = envInt("PDF_ENGINE_HTTP_MAX_CONN_TOTAL", MAX_CONN_PER_ROUTE);
    /** Connection TTL (s). Keep below APIM keep-alive to avoid stale sockets. */
    private static final long CONN_TTL_SECONDS = envLong("PDF_ENGINE_HTTP_CONN_TTL_SECONDS", 60L);
    /** Idle eviction interval (s). Should stay below {@link #CONN_TTL_SECONDS}. */
    private static final long IDLE_EVICT_SECONDS = envLong("PDF_ENGINE_HTTP_IDLE_EVICT_SECONDS", 30L);
    /** Validate leased connection if idle longer than this (ms). Matches HttpClient default, made explicit. */
    private static final int VALIDATE_AFTER_INACTIVITY_MS = envInt("PDF_ENGINE_HTTP_VALIDATE_AFTER_INACTIVITY_MS", 2_000);

    /**
     * Long-lived, thread-safe pooled HTTP client.
     */
    private final CloseableHttpClient httpClient;

    private static final class Holder {
        private static final PdfEngineClientImpl INSTANCE = new PdfEngineClientImpl();
    }

    public static PdfEngineClientImpl getInstance() {
        return Holder.INSTANCE;
    }

    private PdfEngineClientImpl() {
        this(buildDefaultHttpClient());
    }

    /**
     * Visible for tests: allows injecting a mocked or custom client.
     */
    protected PdfEngineClientImpl(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
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
        try (InputStream templateStream = TEMPLATE_STREAM.openStream()) {
            //Encode template and data
            HttpPost request = buildMultipartRequest(pdfEngineRequest, templateStream);

            return makeCall(request, workingDirPath);
        } catch (IOException e) {
            return createErrorResponse(e);
        }
    }

    private HttpPost buildMultipartRequest(
            PdfEngineRequest pdfEngineRequest,
            InputStream templateStream
    ) throws IOException {
        StringBody dataBody = new StringBody(pdfEngineRequest.getData(), ContentType.APPLICATION_JSON);

        //Build the multipart request
        HttpEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addBinaryBody(TEMPLATE_KEY, templateStream.readAllBytes(), ContentType.create("application/zip"), ZIP_FILE_NAME)
                .addPart(DATA_KEY, dataBody)
                .build();

        //Set endpoint and auth key
        HttpPost request = new HttpPost(pdfEngineEndpoint);
        request.setHeader(subKeyHeader);
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
        try (CloseableHttpResponse response = this.httpClient.execute(request)) {
            //Retrieve response
            int statusCode = response.getStatusLine().getStatusCode();
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

    // -----------------------------------------------------------------
    // HTTP client construction
    // -----------------------------------------------------------------

    /**
     * Builds the shared {@link CloseableHttpClient} with a pooled connection
     * manager, sensible timeouts and a retry handler for transient I/O errors.
     */
    private static CloseableHttpClient buildDefaultHttpClient() {
        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager(CONN_TTL_SECONDS, TimeUnit.SECONDS);
        connectionManager.setMaxTotal(MAX_CONN_TOTAL);
        connectionManager.setDefaultMaxPerRoute(MAX_CONN_PER_ROUTE);
        connectionManager.setValidateAfterInactivity(VALIDATE_AFTER_INACTIVITY_MS);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

        return HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(false)
                .setDefaultRequestConfig(requestConfig)
                .setRetryHandler(buildRetryHandler())
                .setConnectionTimeToLive(CONN_TTL_SECONDS, TimeUnit.SECONDS)
                .evictExpiredConnections()
                .evictIdleConnections(IDLE_EVICT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Retry handler for transient I/O failures on APIM (connect timeouts, dropped keep-alive,
     * read timeouts). Excludes non-transient errors (auth/SSL/unknown host). Note: retries on
     * {@link SocketTimeoutException} assume PDF Engine is idempotent — disable if not.
     */
    private static DefaultHttpRequestRetryHandler buildRetryHandler() {
        return new DefaultHttpRequestRetryHandler(
                RETRY_COUNT,
                true,
                List.of(InterruptedIOException.class, UnknownHostException.class, SSLException.class)
        ) {
            @Override
            public boolean retryRequest(
                    IOException exception,
                    int executionCount,
                    org.apache.http.protocol.HttpContext context
            ) {
                if (executionCount > RETRY_COUNT) {
                    return false;
                }
                if (exception instanceof ConnectTimeoutException
                        || exception instanceof NoHttpResponseException
                        || exception instanceof SocketTimeoutException) {
                    return true;
                }
                return super.retryRequest(exception, executionCount, context);
            }
        };
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static int envInt(String name, int defaultValue) {
        return Integer.parseInt(System.getenv().getOrDefault(name, Integer.toString(defaultValue)));
    }

    private static long envLong(String name, long defaultValue) {
        return Long.parseLong(System.getenv().getOrDefault(name, Long.toString(defaultValue)));
    }
}
