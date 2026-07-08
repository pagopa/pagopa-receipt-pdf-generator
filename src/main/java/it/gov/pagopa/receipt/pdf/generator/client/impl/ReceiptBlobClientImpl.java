package it.gov.pagopa.receipt.pdf.generator.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptBlobClient;
import it.gov.pagopa.receipt.pdf.generator.model.response.BlobStorageResponse;

import java.io.InputStream;

/**
 * {@inheritDoc}
 */
public class ReceiptBlobClientImpl implements ReceiptBlobClient {

    private static final String FILE_EXTENSION = ".pdf";

    private final BlobContainerClient blobContainerClient;

    private ReceiptBlobClientImpl() {
        String connectionString = System.getenv("RECEIPTS_STORAGE_CONN_STRING");
        String containerName = System.getenv("BLOB_STORAGE_CONTAINER_NAME");

        this.blobContainerClient = new BlobContainerClientBuilder()
                .connectionString(connectionString)
                .containerName(containerName)
                .buildClient();
    }

    ReceiptBlobClientImpl(BlobContainerClient blobContainerClient) {
        this.blobContainerClient = blobContainerClient;
    }

    /**
     * Bill Pugh singleton holder: the JVM guarantees that the class is loaded
     * (and therefore INSTANCE initialized) lazily and in a thread-safe way.
     */
    private static class SingletonHelper {
        private static final ReceiptBlobClientImpl INSTANCE = new ReceiptBlobClientImpl();
    }

    public static ReceiptBlobClientImpl getInstance() {
        return ReceiptBlobClientImpl.SingletonHelper.INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlobStorageResponse savePdfToBlobStorage(InputStream pdf, String fileName) {
        String fileNamePdf = fileName + FILE_EXTENSION;

        //Get a reference to a blob
        BlobClient blobClient = this.blobContainerClient.getBlobClient(fileNamePdf);

        //Upload the blob
        Response<BlockBlobItem> blockBlobItemResponse = blobClient.uploadWithResponse(
                new BlobParallelUploadOptions(
                        pdf
                ), null, null);

        BlobStorageResponse blobStorageResponse = new BlobStorageResponse();

        //Build response accordingly
        int statusCode = blockBlobItemResponse.getStatusCode();

        if (statusCode == HttpStatus.CREATED.value()) {
            blobStorageResponse.setDocumentName(blobClient.getBlobName());
            blobStorageResponse.setDocumentUrl(blobClient.getBlobUrl());
        }

        blobStorageResponse.setStatusCode(statusCode);

        return blobStorageResponse;
    }
}
