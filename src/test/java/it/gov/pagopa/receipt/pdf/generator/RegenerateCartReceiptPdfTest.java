package it.gov.pagopa.receipt.pdf.generator;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.generator.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartPayment;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.MessageData;
import it.gov.pagopa.receipt.pdf.generator.entity.cart.Payload;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.event.InfoTransaction;
import it.gov.pagopa.receipt.pdf.generator.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.generator.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.CartReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfCartGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.helpdesk.ProblemJson;
import it.gov.pagopa.receipt.pdf.generator.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateCartReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.service.helpdesk.HelpdeskService;
import it.gov.pagopa.receipt.pdf.generator.util.HttpResponseMessageMock;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtilsTest.getBizEventFromFile;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegenerateCartReceiptPdfTest {

    private static final String BIZ_EVENT_ID = "biz-event-id";

    @Mock
    private GenerateCartReceiptPdfService generateCartReceiptPdfServiceMock;
    @Mock
    private CartReceiptCosmosService cartReceiptCosmosServiceMock;
    @Mock
    private HelpdeskService helpdeskServiceMock;
    @Mock
    private BizEventCosmosClient bizEventCosmosClient;
    @Mock
    private ExecutionContext executionContextMock;

    @InjectMocks
    private RegenerateCartReceiptPdf sut;

    @Spy
    private OutputBinding<CartForReceipt> documentdb;
    @Captor
    private ArgumentCaptor<CartForReceipt> receiptBindingCaptor;
    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    @BeforeEach
    void setUp() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));
    }

    @Test
    @SneakyThrows
    void regeneratePDFSuccessWithExistingReceipt() {
        List<BizEvent> bizEventList = buildBizEvents();

        CartForReceipt existingCart = buildExistingCart(bizEventList);
        CartForReceipt newCart = buildCart(bizEventList, CartStatusType.INSERTED);


        doReturn(bizEventList).when(bizEventCosmosClient).getAllCartBizEventDocument(anyString());
        doReturn(newCart).when(helpdeskServiceMock).buildCart(any());
        doReturn(existingCart).when(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        doReturn(new PdfCartGeneration()).when(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), anyList(), any());
        doReturn(true).when(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                newCart.getEventId(),
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_OK, response.getStatusCode());

        verify(bizEventCosmosClient).getAllCartBizEventDocument(anyString());
        verify(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), anyList(), any());
        verify(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());
        verify(documentdb).setValue(receiptBindingCaptor.capture());

        CartForReceipt savedReceipt = receiptBindingCaptor.getValue();
        assertNotNull(savedReceipt);
        assertEquals(existingCart.getId(), savedReceipt.getId());
        assertNotNull(savedReceipt.getPayload().getMessagePayer());
        savedReceipt.getPayload().getCart().forEach(cartPayment -> {
            assertNotNull(cartPayment.getMessageDebtor());
        });
    }

    @Test
    @SneakyThrows
    void regeneratePDFSuccessWithoutExistingReceipt() {
        List<BizEvent> bizEventList = buildBizEvents();

        CartForReceipt newCart = buildCart(bizEventList, CartStatusType.INSERTED);

        doReturn(bizEventList).when(bizEventCosmosClient).getAllCartBizEventDocument(anyString());
        doReturn(newCart).when(helpdeskServiceMock).buildCart(any());
        doThrow(CartNotFoundException.class).when(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        doReturn(new PdfCartGeneration()).when(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), anyList(), any());
        doReturn(true).when(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                newCart.getEventId(),
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_OK, response.getStatusCode());

        verify(bizEventCosmosClient).getAllCartBizEventDocument(anyString());
        verify(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), anyList(), any());
        verify(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());
        verify(documentdb).setValue(receiptBindingCaptor.capture());

        CartForReceipt savedReceipt = receiptBindingCaptor.getValue();
        assertNotNull(savedReceipt);
        assertEquals(newCart.getId(), savedReceipt.getId());
        assertNull(savedReceipt.getPayload().getMessagePayer());
        savedReceipt.getPayload().getCart().forEach(cartPayment -> {
            assertNull(cartPayment.getMessageDebtor());
        });
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailBizEventNotFound() {
        doReturn(Collections.emptyList()).when(bizEventCosmosClient).getAllCartBizEventDocument(anyString());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                "1",
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals("BizEvents for cart 1 not found", body.getDetail());

        verify(cartReceiptCosmosServiceMock, never()).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @ParameterizedTest
    @EnumSource(value = BizEventStatusType.class, names = {"DONE"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void regeneratePDFFailBizEventStatusNotValid(BizEventStatusType status) {
        List<BizEvent> bizEventList = buildBizEvents();
        bizEventList.get(0).setEventStatus(status);

        doReturn(bizEventList).when(bizEventCosmosClient).getAllCartBizEventDocument(anyString());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                "1",
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals("Biz event is in invalid status " + status, body.getDetail());

        verify(cartReceiptCosmosServiceMock, never()).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailBizEventFiscalCodeNotValid() {
        List<BizEvent> bizEventList = buildBizEvents();
        BizEvent bizEvent = bizEventList.get(0);
        bizEvent.getDebtor().setEntityUniqueIdentifierValue("ANONIMO");
        bizEvent.setPayer(null);

        doReturn(bizEventList).when(bizEventCosmosClient).getAllCartBizEventDocument(anyString());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                "1",
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals("Biz event is in invalid because debtor's and payer's identifiers are missing or not valid", body.getDetail());

        verify(cartReceiptCosmosServiceMock, never()).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailBizEventNotValidForEcommerceFilter() {
        List<BizEvent> bizEventList = buildBizEvents();
        BizEvent bizEvent = bizEventList.get(0);
        bizEvent.getTransactionDetails()
                .setInfo(InfoTransaction.builder()
                        .clientId("CHECKOUT")
                        .build());

        doReturn(bizEventList).when(bizEventCosmosClient).getAllCartBizEventDocument(anyString());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                "1",
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals("Biz event is in invalid because it is from e-commerce and e-commerce filter is enabled", body.getDetail());

        verify(cartReceiptCosmosServiceMock, never()).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailBizEventNotValidForLegacyCart() {
        List<BizEvent> bizEventList = buildBizEvents();
        BizEvent bizEvent = bizEventList.get(0);
        bizEvent.getPaymentInfo().setTotalNotice(null);
        bizEvent.getPaymentInfo().setAmount("1");

        doReturn(bizEventList).when(bizEventCosmosClient).getAllCartBizEventDocument(anyString());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                "1",
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals("Biz event is in invalid because contain either an invalid amount value or it is a legacy cart element", body.getDetail());

        verify(cartReceiptCosmosServiceMock, never()).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailBizEventNotValidInvalidTotalNotice() {
        List<BizEvent> bizEventList = buildBizEvents();
        BizEvent bizEvent = bizEventList.get(0);
        bizEvent.getPaymentInfo().setTotalNotice("1");

        doReturn(bizEventList).when(bizEventCosmosClient).getAllCartBizEventDocument(anyString());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                "1",
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals("Failed to regenerate cart, the expected total notice 1 does not match the number of biz events 5", body.getDetail());

        verify(cartReceiptCosmosServiceMock, never()).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailBuildCartError() {
        List<BizEvent> bizEventList = buildBizEvents();

        doReturn(bizEventList).when(bizEventCosmosClient).getAllCartBizEventDocument(anyString());
        doThrow(PDVTokenizerException.class).when(helpdeskServiceMock).buildCart(any());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                "1",
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertTrue(body.getDetail().startsWith("An error occurred while building cart receipt:"));

        verify(cartReceiptCosmosServiceMock, never()).getCartForReceipt(anyString());
        verify(generateCartReceiptPdfServiceMock, never()).generateCartReceipts(any(), any(), any());
        verify(generateCartReceiptPdfServiceMock, never()).verifyAndUpdateCartReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailVerifyReceipt() {
        List<BizEvent> bizEventList = buildBizEvents();

        CartForReceipt newCart = buildCart(bizEventList, CartStatusType.INSERTED);

        doReturn(bizEventList).when(bizEventCosmosClient).getAllCartBizEventDocument(anyString());
        doReturn(newCart).when(helpdeskServiceMock).buildCart(any());
        doThrow(CartNotFoundException.class).when(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        doReturn(new PdfCartGeneration()).when(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), anyList(), any());
        doReturn(false).when(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                newCart.getEventId(),
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals("Unable to generate PDF cart receipt", body.getDetail());

        verify(bizEventCosmosClient).getAllCartBizEventDocument(anyString());
        verify(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), anyList(), any());
        verify(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());
        verify(documentdb, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailVerifyReceiptThrowException() {
        List<BizEvent> bizEventList = buildBizEvents();

        CartForReceipt newCart = buildCart(bizEventList, CartStatusType.INSERTED);

        doReturn(bizEventList).when(bizEventCosmosClient).getAllCartBizEventDocument(anyString());
        doReturn(newCart).when(helpdeskServiceMock).buildCart(any());
        doThrow(CartNotFoundException.class).when(cartReceiptCosmosServiceMock).getCartForReceipt(anyString());
        doReturn(new PdfCartGeneration()).when(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), anyList(), any());
        doThrow(CartReceiptGenerationNotToRetryException.class).when(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                newCart.getEventId(),
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertTrue(body.getDetail().startsWith("Error during cart receipt generation: "));

        verify(bizEventCosmosClient).getAllCartBizEventDocument(anyString());
        verify(generateCartReceiptPdfServiceMock).generateCartReceipts(any(), anyList(), any());
        verify(generateCartReceiptPdfServiceMock).verifyAndUpdateCartReceipt(any(), any());
        verify(documentdb, never()).setValue(any());
    }

    private CartForReceipt buildExistingCart(List<BizEvent> bizEventList) {
        List<CartPayment> cartPayments = new ArrayList<>();
        for (BizEvent bizEvent : bizEventList) {
            cartPayments.add(CartPayment.builder()
                    .bizEventId(bizEvent.getId())
                    .mdAttach(buildMdAttach())
                    .messageDebtor(buildMessageDebtor())
                    .build());
        }
        return CartForReceipt.builder()
                .id("idCart")
                .eventId("idCart")
                .payload(Payload.builder()
                        .mdAttachPayer(buildMdAttach())
                        .messagePayer(buildMessageDebtor())
                        .cart(cartPayments)
                        .totalNotice(bizEventList.size())
                        .build())
                .status(CartStatusType.IO_NOTIFIED)
                .inserted_at(41235)
                .generated_at(1141235)
                .notified_at(2341235)
                .build();
    }

    private CartForReceipt buildCart(List<BizEvent> bizEventList, CartStatusType statusType) {
        List<CartPayment> cartPayments = new ArrayList<>();
        for (BizEvent bizEvent : bizEventList) {
            cartPayments.add(CartPayment.builder()
                    .bizEventId(bizEvent.getId())
                    .build());
        }
        return CartForReceipt.builder()
                .id("idCart")
                .eventId("idCart")
                .payload(Payload.builder()
                        .cart(cartPayments)
                        .totalNotice(bizEventList.size())
                        .build())
                .status(statusType)
                .build();
    }

    private MessageData buildMessageDebtor() {
        return MessageData.builder()
                .id("")
                .subject("subject")
                .markdown("markdown")
                .build();
    }

    private ReceiptMetadata buildMdAttach() {
        return ReceiptMetadata.builder()
                .name("name")
                .url("url")
                .build();
    }

    private List<BizEvent> buildBizEvents() throws IOException {
        List<BizEvent> bizEventList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            BizEvent bizEvent = getBizEventFromFile("biz-events/biz-event.json");
            String bizEvenId = BIZ_EVENT_ID + i;
            bizEvent.setId(bizEvenId);
            bizEvent.getPaymentInfo().setTotalNotice("5");
            bizEventList.add(bizEvent);
        }
        return bizEventList;
    }
}