package it.gov.pagopa.receipt.pdf.generator.service.impl;

import it.gov.pagopa.receipt.pdf.generator.client.ReceiptBlobClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.ReceiptBlobClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.generator.exception.SavePDFToBlobException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.generator.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.generator.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptBlobStorageService;
import lombok.Setter;
import org.apache.http.HttpStatus;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

public class ReceiptBlobStorageServiceImpl implements ReceiptBlobStorageService {

    private final ReceiptBlobClient receiptBlobClient;
    @Setter
    private long minFileLength = Long.parseLong(
            System.getenv().getOrDefault("MIN_PDF_LENGTH", "10000"));

    public ReceiptBlobStorageServiceImpl() {
        this.receiptBlobClient = ReceiptBlobClientImpl.getInstance();
    }

    ReceiptBlobStorageServiceImpl(ReceiptBlobClient receiptBlobClient) {
        this.receiptBlobClient = receiptBlobClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PdfMetadata saveToBlobStorage(
            PdfEngineResponse pdfEngineResponse,
            String blobName
    ) throws SavePDFToBlobException {
        String tempPdfPath = pdfEngineResponse.getTempPdfPath();

        if (new File(tempPdfPath).length() < minFileLength) {
            throw new SavePDFToBlobException("Minimum file size not reached", ReasonErrorCode.ERROR_BLOB_STORAGE.getCode());
        }

        BlobStorageResponse blobStorageResponse;
        //Save to Blob Storage
        try (BufferedInputStream pdfStream = new BufferedInputStream(new FileInputStream(tempPdfPath))) {
            blobStorageResponse = this.receiptBlobClient.savePdfToBlobStorage(pdfStream, blobName);
        } catch (Exception e) {
            throw new SavePDFToBlobException("Error saving pdf to blob storage", ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), e);
        }

        if (blobStorageResponse.getStatusCode() != com.microsoft.azure.functions.HttpStatus.CREATED.value()) {
            String errMsg = String.format("Error saving pdf to blob storage, storage responded with status %s",
                    blobStorageResponse.getStatusCode());
            throw new SavePDFToBlobException(errMsg, ReasonErrorCode.ERROR_BLOB_STORAGE.getCode());
        }

        //Update PDF metadata
        return PdfMetadata.builder()
                .documentName(blobStorageResponse.getDocumentName())
                .documentUrl(blobStorageResponse.getDocumentUrl())
                .statusCode(HttpStatus.SC_OK)
                .build();
    }
}
