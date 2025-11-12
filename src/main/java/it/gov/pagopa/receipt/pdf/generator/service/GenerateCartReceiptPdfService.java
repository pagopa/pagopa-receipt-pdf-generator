package it.gov.pagopa.receipt.pdf.generator.service;

import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.exception.CartReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfCartGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;

import java.nio.file.Path;
import java.util.List;

public interface GenerateCartReceiptPdfService {

    /**
     * Handles conditionally the generation of the PDF's receipts based on the provided bizEvents
     *
     * @param cartForReceipt  the Cart receipt that hold the status of the elaboration
     * @param listOfBizEvents List of Biz-events from queue message
     * @return {@link PdfCartGeneration} object with the result of the PDF generation and store or the relatives error messages
     */
    PdfCartGeneration generateCartReceipts(CartForReceipt cartForReceipt, List<BizEvent> listOfBizEvents, Path workingDirPath);

    /**
     * Verifies if the PDF generation process succeeded or not, and update the cart receipt with the result
     * In case of errors updates the cart receipt status and error message.
     *
     * @param cartForReceipt the Cart receipt that hold the status of the elaboration
     * @param pdfCartGeneration  {@link PdfGeneration} object with the result of the PDF generation
     * @return true if the process succeeded, otherwise false
     * @throws CartReceiptGenerationNotToRetryException when the cart receipt generation fail with an error that will not be retried
     */
    boolean verifyAndUpdateCartReceipt(
            CartForReceipt cartForReceipt,
            PdfCartGeneration pdfCartGeneration
    ) throws CartReceiptGenerationNotToRetryException;
}
