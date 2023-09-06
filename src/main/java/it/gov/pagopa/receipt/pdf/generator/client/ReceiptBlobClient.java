package it.gov.pagopa.receipt.pdf.generator.client;

import it.gov.pagopa.receipt.pdf.generator.model.response.BlobStorageResponse;

import java.io.InputStream;

public interface ReceiptBlobClient {

    BlobStorageResponse savePdfToBlobStorage(InputStream pdf, String fileName);
}
