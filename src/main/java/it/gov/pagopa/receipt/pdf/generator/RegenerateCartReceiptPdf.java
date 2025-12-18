package it.gov.pagopa.receipt.pdf.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.generator.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartPayment;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.MessageData;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.CartReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfCartGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.helpdesk.ProblemJson;
import it.gov.pagopa.receipt.pdf.generator.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateCartReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.service.helpdesk.HelpdeskService;
import it.gov.pagopa.receipt.pdf.generator.service.helpdesk.impl.HelpdeskServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.service.impl.CartReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.service.impl.GenerateCartReceiptPdfServiceImpl;
import it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils;
import it.gov.pagopa.receipt.pdf.generator.utils.ReceiptGeneratorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static it.gov.pagopa.receipt.pdf.generator.utils.HelpdeskUtils.isBizEventInvalid;


/**
 * Azure Functions with Azure Http trigger.
 */
public class RegenerateCartReceiptPdf {

    private static final Logger logger = LoggerFactory.getLogger(RegenerateCartReceiptPdf.class);

    public static final String LOG_FORMAT = "[{}] {}";

    private final BizEventCosmosClient bizEventCosmosClient;
    private final CartReceiptCosmosService cartReceiptCosmosService;
    private final HelpdeskService helpdeskService;
    private final GenerateCartReceiptPdfService generateCartReceiptPdfService;

    public RegenerateCartReceiptPdf() {
        this.helpdeskService = new HelpdeskServiceImpl();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
        this.cartReceiptCosmosService = new CartReceiptCosmosServiceImpl();
        this.generateCartReceiptPdfService = new GenerateCartReceiptPdfServiceImpl();
    }

    RegenerateCartReceiptPdf(
            BizEventCosmosClient bizEventCosmosClient,
            CartReceiptCosmosService cartReceiptCosmosService,
            HelpdeskService helpdeskService,
            GenerateCartReceiptPdfService generateCartReceiptPdfService
    ) {
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.cartReceiptCosmosService = cartReceiptCosmosService;
        this.helpdeskService = helpdeskService;
        this.generateCartReceiptPdfService = generateCartReceiptPdfService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs
     *
     * @return response with HttpStatus.OK
     */
    @FunctionName("RegenerateCartReceiptFunc")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RegenerateCartReceiptPdfFuncTrigger",
                    methods = {HttpMethod.POST},
                    route = "cart-receipts/{cart-id}/regenerate-receipt-pdf",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("cart-id") String cartId,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    containerName = "cart-for-receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<CartForReceipt> documentdb,
            final ExecutionContext context
    ) throws IOException {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        List<BizEvent> bizEventList = this.bizEventCosmosClient.getAllCartBizEventDocument(cartId);
        if (bizEventList.isEmpty()) {
            String errMsg = String.format("BizEvents for cart %s not found", cartId);
            logger.error(LOG_FORMAT, context.getFunctionName(), errMsg);
            return buildErrorResponse(request, HttpStatus.BAD_REQUEST, errMsg);
        }

        for (BizEvent bizEvent : bizEventList) {
            HelpdeskUtils.BizEventValidityCheck bizEventValidityCheck = isBizEventInvalid(bizEvent);
            if (bizEventValidityCheck.invalid()) {
                return buildErrorResponse(request, HttpStatus.BAD_REQUEST, bizEventValidityCheck.error());
            }
            Integer totalNotice = HelpdeskUtils.getTotalNotice(bizEvent, context, logger);
            if (totalNotice != bizEventList.size()) {
                String errMsg = String.format("Failed to regenerate cart, the expected total notice %s does not match the number of biz events %s",
                        totalNotice, bizEventList.size());
                logger.error(LOG_FORMAT, context.getFunctionName(), errMsg);
                return buildErrorResponse(
                        request,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        errMsg
                );
            }
        }

        CartForReceipt cart;
        try {
            cart = this.helpdeskService.buildCart(bizEventList);
        } catch (JsonProcessingException | PDVTokenizerException e) {
            return buildErrorResponse(
                    request,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "An error occurred while building cart receipt: " + e.getMessage()
            );
        }
        addExistingCartInfoIfExist(cartId, context, cart);

        PdfCartGeneration pdfGeneration = generatePDFReceipt(cart, bizEventList);
        try {
            boolean success = this.generateCartReceiptPdfService.verifyAndUpdateCartReceipt(cart, pdfGeneration);
            if (success) {
                cart.setInserted_at(System.currentTimeMillis());
                cart.setGenerated_at(System.currentTimeMillis());
                cart.setStatus(CartStatusType.IO_NOTIFIED);
            } else {
                return buildErrorResponse(
                        request,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unable to generate PDF cart receipt"
                );
            }
        } catch (CartReceiptGenerationNotToRetryException e) {
            logger.error("[{}] Not retryable error occurred while generating the cart receipt with event id {}",
                    context.getFunctionName(), cart.getEventId(), e);
            return buildErrorResponse(
                    request,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error during cart receipt generation: " + e.getMessage()
            );
        }

        // updated the cart on cosmos
        documentdb.setValue(cart);

        return request.createResponseBuilder(HttpStatus.OK)
                .body("Regenerate cart completed successfully")
                .build();
    }

    private void addExistingCartInfoIfExist(String cartId, ExecutionContext context, CartForReceipt cart) {
        try {
            CartForReceipt existingCart = this.cartReceiptCosmosService.getCartForReceipt(cartId);
            if (CartStatusType.IO_NOTIFIED.equals(existingCart.getStatus())) {
                cart.getPayload().setMessagePayer(existingCart.getPayload().getMessagePayer());

                Map<String, MessageData> cartInfoMap = groupCartInfoByBizEventId(existingCart.getPayload().getCart());
                cart.getPayload().getCart().forEach(cartPayment -> cartPayment.setMessageDebtor(cartInfoMap.get(cartPayment.getBizEventId())));
                cart.setNotified_at(existingCart.getNotified_at());
            }
        } catch (CartNotFoundException e) {
            logger.info("[{}] Cart receipt not found with the provided cart id, a new receipt will be generated",
                    context.getFunctionName());
        }
    }

    private PdfCartGeneration generatePDFReceipt(
            CartForReceipt receipt,
            List<BizEvent> bizEventList
    ) throws IOException {
        Path workingDirPath = ReceiptGeneratorUtils.createWorkingDirectory();
        try {
            return this.generateCartReceiptPdfService.generateCartReceipts(receipt, bizEventList, workingDirPath);
        } finally {
            ReceiptGeneratorUtils.deleteTempFolder(workingDirPath, logger);
        }
    }

    private HttpResponseMessage buildErrorResponse(
            HttpRequestMessage<Optional<String>> request,
            HttpStatus httpStatus,
            String errMsg
    ) {
        return request
                .createResponseBuilder(httpStatus)
                .body(ProblemJson.builder()
                        .title(httpStatus.name())
                        .detail(errMsg)
                        .status(httpStatus.value())
                        .build())
                .build();
    }

    private Map<String, MessageData> groupCartInfoByBizEventId(List<CartPayment> cart) {
        return cart.stream()
                .collect(Collectors.toMap(
                        CartPayment::getBizEventId,
                        CartPayment::getMessageDebtor
                ));
    }
}