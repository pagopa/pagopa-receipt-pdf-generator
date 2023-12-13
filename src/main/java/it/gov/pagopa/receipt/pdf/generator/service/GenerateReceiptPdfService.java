package it.gov.pagopa.receipt.pdf.generator.service;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;

import java.nio.file.Path;
import java.util.List;

public interface GenerateReceiptPdfService {

    /**
     * Handles conditionally the generation of the PDF's receipts based on the provided bizEvent
     *
     * @param receipt the Receipt that hold the status of the elaboration
     * @param listOfBizEvents List of Biz-events from queue message
     * @return {@link PdfGeneration} object with the result of the PDF generation and store or the relatives error messages
     */
    PdfGeneration generateReceipts(Receipt receipt, List<BizEvent> listOfBizEvents, Path workingDirPath);

    /**
     * Verifies if the PDF generation process succeeded or not, and update the receipt with the result
     * In case of errors updates the receipt status and error message.
     *
     * @param receipt the Receipt that hold the status of the elaboration
     * @param pdfGeneration {@link PdfGeneration} object with the result of the PDF generation
     * @return true if the process succeeded, otherwise false
     * @throws ReceiptGenerationNotToRetryException when the receipt generation fail with an error that will not be retried
     */
    boolean verifyAndUpdateReceipt(Receipt receipt, PdfGeneration pdfGeneration) throws ReceiptGenerationNotToRetryException;
}
