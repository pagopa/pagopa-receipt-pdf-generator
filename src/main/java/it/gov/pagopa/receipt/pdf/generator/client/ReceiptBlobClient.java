package it.gov.pagopa.receipt.pdf.generator.client;

import it.gov.pagopa.receipt.pdf.generator.model.response.BlobStorageResponse;

import java.io.InputStream;

/**
 * Client for the Blob Storage
 */
public interface ReceiptBlobClient {

    /**
     * Handles saving the PDF to the blob storage
     *
     * @param pdf      PDF file
     * @param fileName Filename to save the PDF with
     * @return blob storage response with PDF metadata or error message and status
     */
    BlobStorageResponse savePdfToBlobStorage(InputStream pdf, String fileName);
}
