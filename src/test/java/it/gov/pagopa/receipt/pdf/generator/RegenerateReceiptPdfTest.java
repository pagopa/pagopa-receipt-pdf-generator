package it.gov.pagopa.receipt.pdf.generator;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.generator.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.event.InfoTransaction;
import it.gov.pagopa.receipt.pdf.generator.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.helpdesk.ProblemJson;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.service.ReceiptCosmosService;
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

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtilsTest.getBizEventFromFile;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegenerateReceiptPdfTest {

    private static final long ORIGINAL_GENERATED_AT = 0L;

    @Mock
    private GenerateReceiptPdfService generateReceiptPdfServiceMock;
    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;
    @Mock
    private HelpdeskService helpdeskServiceMock;
    @Mock
    private BizEventCosmosClient bizEventCosmosClient;
    @Mock
    private ExecutionContext executionContextMock;

    @InjectMocks
    private RegenerateReceiptPdf sut;

    @Spy
    private OutputBinding<Receipt> documentdb;
    @Captor
    private ArgumentCaptor<Receipt> receiptBindingCaptor;
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
        BizEvent bizEvent = getBizEventFromFile("biz-events/biz-event.json");

        Receipt existingReceipt = buildReceiptWithStatus();
        Receipt newReceipt = buildNewCreatedReceiptWithStatus(ReceiptStatusType.INSERTED);

        doReturn(bizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(newReceipt).when(helpdeskServiceMock).createReceipt(any());
        doReturn(existingReceipt).when(receiptCosmosServiceMock).getReceipt(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                bizEvent.getId(),
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_OK, response.getStatusCode());

        verify(receiptCosmosServiceMock).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentdb).setValue(receiptBindingCaptor.capture());

        Receipt savedReceipt = receiptBindingCaptor.getValue();
        assertNotNull(savedReceipt);
        assertEquals(existingReceipt.getId(), savedReceipt.getId());
    }

    @Test
    @SneakyThrows
    void regeneratePDFSuccessWithoutExistingReceipt() {
        BizEvent bizEvent = getBizEventFromFile("biz-events/biz-event.json");

        Receipt newReceipt = buildNewCreatedReceiptWithStatus(ReceiptStatusType.INSERTED);

        doReturn(bizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(newReceipt).when(helpdeskServiceMock).createReceipt(any());
        doThrow(ReceiptNotFoundException.class).when(receiptCosmosServiceMock).getReceipt(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                bizEvent.getId(),
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_OK, response.getStatusCode());

        verify(receiptCosmosServiceMock).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentdb).setValue(receiptBindingCaptor.capture());

        Receipt savedReceipt = receiptBindingCaptor.getValue();
        assertNotNull(savedReceipt);
        assertEquals(newReceipt.getId(), savedReceipt.getId());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailInvalidParameter() {
        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                null,
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals("Missing valid eventId parameter", body.getDetail());

        verify(receiptCosmosServiceMock, never()).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailBizEventNotFound() {
        doThrow(BizEventNotFoundException.class).when(bizEventCosmosClient).getBizEventDocument(anyString());

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
        assertEquals("BizEvent not found with id 1", body.getDetail());

        verify(receiptCosmosServiceMock, never()).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailBizEventNull() {
        doReturn(null).when(bizEventCosmosClient).getBizEventDocument(anyString());

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
        assertEquals("Biz event is null", body.getDetail());

        verify(receiptCosmosServiceMock, never()).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @ParameterizedTest
    @EnumSource(value = BizEventStatusType.class, names = {"DONE"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void regeneratePDFFailBizEventStatusNotValid(BizEventStatusType status) {
        BizEvent bizEvent = getBizEventFromFile("biz-events/biz-event.json");
        bizEvent.setEventStatus(status);

        doReturn(bizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                bizEvent.getId(),
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals("Biz event is in invalid status " + status, body.getDetail());

        verify(receiptCosmosServiceMock, never()).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailBizEventFiscalCodeNotValid() {
        BizEvent bizEvent = getBizEventFromFile("biz-events/biz-event.json");
        bizEvent.getDebtor().setEntityUniqueIdentifierValue("ANONIMO");
        bizEvent.setPayer(null);

        doReturn(bizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                bizEvent.getId(),
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals("Biz event is in invalid because debtor's and payer's identifiers are missing or not valid", body.getDetail());

        verify(receiptCosmosServiceMock, never()).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailBizEventNotValidForEcommerceFilter() {
        BizEvent bizEvent = getBizEventFromFile("biz-events/biz-event.json");
        bizEvent.getTransactionDetails()
                .setInfo(InfoTransaction.builder()
                        .clientId("CHECKOUT")
                        .build());

        doReturn(bizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                bizEvent.getId(),
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals("Biz event is in invalid because it is from e-commerce and e-commerce filter is enabled", body.getDetail());

        verify(receiptCosmosServiceMock, never()).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailBuildReceiptError() {
        BizEvent bizEvent = getBizEventFromFile("biz-events/biz-event.json");

        Receipt newReceipt = buildNewCreatedReceiptWithStatus(ReceiptStatusType.FAILED);

        doReturn(bizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(newReceipt).when(helpdeskServiceMock).createReceipt(any());
        doThrow(ReceiptNotFoundException.class).when(receiptCosmosServiceMock).getReceipt(anyString());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                bizEvent.getId(),
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertTrue(body.getDetail().startsWith("Failed to re-create receipt entity with eventId"));

        verify(receiptCosmosServiceMock).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock, never()).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock, never()).verifyAndUpdateReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailVerifyReceipt() {
        BizEvent bizEvent = getBizEventFromFile("biz-events/biz-event.json");

        Receipt newReceipt = buildNewCreatedReceiptWithStatus(ReceiptStatusType.INSERTED);

        doReturn(bizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(newReceipt).when(helpdeskServiceMock).createReceipt(any());
        doThrow(ReceiptNotFoundException.class).when(receiptCosmosServiceMock).getReceipt(anyString());
        doReturn(false).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                bizEvent.getId(),
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertEquals("Unable to generate PDF receipt", body.getDetail());

        verify(receiptCosmosServiceMock).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    @Test
    @SneakyThrows
    void regeneratePDFFailVerifyReceiptThrowException() {
        BizEvent bizEvent = getBizEventFromFile("biz-events/biz-event.json");

        Receipt newReceipt = buildNewCreatedReceiptWithStatus(ReceiptStatusType.INSERTED);

        doReturn(bizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(newReceipt).when(helpdeskServiceMock).createReceipt(any());
        doThrow(ReceiptNotFoundException.class).when(receiptCosmosServiceMock).getReceipt(anyString());
        doThrow(ReceiptGenerationNotToRetryException.class).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(
                requestMock,
                bizEvent.getId(),
                documentdb,
                executionContextMock
        ));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusCode());
        ProblemJson body = (ProblemJson) response.getBody();
        assertNotNull(body);
        assertTrue(body.getDetail().startsWith("Error during receipt generation: "));

        verify(receiptCosmosServiceMock).getReceipt(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        verify(documentdb, never()).setValue(receiptBindingCaptor.capture());
    }

    private Receipt buildReceiptWithStatus() {
        return Receipt.builder()
                .id("id")
                .eventData(EventData.builder()
                        .debtorFiscalCode("cd debtor")
                        .payerFiscalCode("cf payer")
                        .build())
                .mdAttach(ReceiptMetadata.builder().name("DEBTOR_NAME").url("DEBTOR_URL").build())
                .mdAttachPayer(ReceiptMetadata.builder().name("PAYER_NAME").url("PAYER_URL").build())
                .eventId("biz-event-id")
                .status(ReceiptStatusType.INSERTED)
                .generated_at(ORIGINAL_GENERATED_AT)
                .inserted_at(0L)
                .notified_at(0L)
                .build();
    }

    private Receipt buildNewCreatedReceiptWithStatus(ReceiptStatusType receiptStatusType) {
        CartItem ci = CartItem.builder().payeeName("payee").subject("TARI").build();
        return Receipt.builder()
                .id("new_created_receipt_id")
                .eventData(EventData.builder()
                        .debtorFiscalCode("tokenizedDebtorFiscalCode")
                        .payerFiscalCode("tokenizedPayerFiscalCode")
                        .cart(List.of(ci))
                        .transactionCreationDate(new Date().toString())
                        .build())
                .eventId("biz-event-id")
                .status(receiptStatusType)
                .build();
    }
}