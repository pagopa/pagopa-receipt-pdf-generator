package it.gov.pagopa.receipt.pdf.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.generator.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.generator.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.generator.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.generator.model.request.RegenerateReceiptRequest;
import it.gov.pagopa.receipt.pdf.generator.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.generator.util.HttpResponseMessageMock;
import it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtils;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegenerateReceiptPdfTest {

    private static final BizEvent bizEvent;

    static {
        try {
            bizEvent = ObjectMapperUtils.mapString("{\"id\":\"variant062-a330-4210-9c67-465b7d641aVS\",\"version\":\"2\",\"idPaymentManager\":null,\"complete\":\"false\",\"receiptId\":\"9a9bad2caf604b86a339476373c659b0\",\"missingInfo\":[\"idPaymentManager\",\"psp.pspPartitaIVA\",\"paymentInfo.primaryCiIncurredFee\",\"paymentInfo.idBundle\",\"paymentInfo.idCiBundle\",\"paymentInfo.metadata\"],\"debtorPosition\":{\"modelType\":\"2\",\"noticeNumber\":\"302119891614290410\",\"iuv\":\"02119891614290410\"},\"creditor\":{\"idPA\":\"66666666666\",\"idBrokerPA\":\"66666666666\",\"idStation\":\"66666666666_01\",\"companyName\":\"PA paolo\",\"officeName\":\"office PA\"},\"psp\":{\"idPsp\":\"60000000001\",\"idBrokerPsp\":\"60000000001\",\"idChannel\":\"60000000001_01\",\"psp\":\"PSP Paolo\",\"pspPartitaIVA\":null,\"pspFiscalCode\":\"CF60000000006\",\"channelDescription\":\"app\"},\"debtor\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"payer\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"paymentInfo\":{\"paymentDateTime\":\"2023-04-12T16:21:39.022486\",\"applicationDate\":\"2021-10-01\",\"transferDate\":\"2021-10-02\",\"dueDate\":\"2021-07-31\",\"paymentToken\":\"9a9bad2caf604b86a339476373c659b0\",\"amount\":\"7000\",\"fee\":\"200\",\"primaryCiIncurredFee\":null,\"idBundle\":null,\"idCiBundle\":null,\"totalNotice\":\"1\",\"paymentMethod\":\"creditCard\",\"touchpoint\":\"app\",\"remittanceInformation\":\"TARI 2021\",\"description\":\"TARI 2021\",\"metadata\":null},\"transferList\":[{\"idTransfer\":\"1\",\"fiscalCodePA\":\"77777777777\",\"companyName\":\"Pa Salvo\",\"amount\":\"7000\",\"transferCategory\":\"0101101IM\",\"remittanceInformation\":\"TARI Comune EC_TE\",\"metadata\":null,\"mbdattachment\":null,\"iban\":\"IT96R0123454321000000012345\"}],\"transactionDetails\":{\"transaction\":{\"psp\":{\"businessName\":\"Nexi\"}},\"wallet\":{\"info\":{\"brand\":\"MASTER\"}}},\"timestamp\":1686919660002,\"properties\":{},\"eventStatus\":\"DONE\",\"eventRetryEnrichmentCount\":0,\"eventTriggeredBySchedule\":false,\"eventErrorMessage\":null}",BizEvent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static final long ORIGINAL_GENERATED_AT = 0L;

    private GenerateReceiptPdfService generateReceiptPdfServiceMock;
    private ReceiptCosmosClient receiptCosmosClientMock;
    private BizEventCosmosClient bizEventCosmosClient;
    private ExecutionContext executionContextMock;
    private RegenerateReceiptPdf sut;

    @BeforeEach
    void setUp() {
        generateReceiptPdfServiceMock = mock(GenerateReceiptPdfService.class);
        receiptCosmosClientMock = mock(ReceiptCosmosClient.class);
        bizEventCosmosClient = mock(BizEventCosmosClient.class);
        executionContextMock = mock(ExecutionContext.class);

        sut = spy(new RegenerateReceiptPdf(
                bizEventCosmosClient, receiptCosmosClientMock, generateReceiptPdfServiceMock));
    }

    @Test
    @SneakyThrows
    void regeneratePDFSuccess() {
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);

        doReturn(bizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        RegenerateReceiptRequest regenerateReceiptRequest = new RegenerateReceiptRequest();
        regenerateReceiptRequest.setEventId("1");

        HttpRequestMessage<Optional<RegenerateReceiptRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(regenerateReceiptRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(200,assertDoesNotThrow(() -> sut.run(request, executionContextMock)).getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
    }

    @Test
    @SneakyThrows
    void regeneratePDFInsuccessMissingRequestData() {

        RegenerateReceiptRequest regenerateReceiptRequest = new RegenerateReceiptRequest();

        HttpRequestMessage<Optional<RegenerateReceiptRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(regenerateReceiptRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(400,assertDoesNotThrow(() -> sut.run(request, executionContextMock)).getStatusCode());

        verifyNoInteractions(receiptCosmosClientMock);
        verifyNoInteractions(generateReceiptPdfServiceMock);
    }

    @Test
    @SneakyThrows
    void regeneratePDFErrorMissingEventData() {
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);
        receipt.setEventData(null);

        doReturn(bizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        RegenerateReceiptRequest regenerateReceiptRequest = new RegenerateReceiptRequest();
        regenerateReceiptRequest.setEventId("1");

        HttpRequestMessage<Optional<RegenerateReceiptRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(regenerateReceiptRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(400,assertDoesNotThrow(() -> sut.run(request, executionContextMock)).getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
    }

    @Test
    @SneakyThrows
    void regeneratePDFErrorReceiptNotFound() {

        doReturn(bizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doThrow(new ReceiptNotFoundException("KO")).when(receiptCosmosClientMock).getReceiptDocument(anyString());

        RegenerateReceiptRequest regenerateReceiptRequest = new RegenerateReceiptRequest();
        regenerateReceiptRequest.setEventId("1");

        HttpRequestMessage<Optional<RegenerateReceiptRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(regenerateReceiptRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(400,assertDoesNotThrow(() -> sut.run(request, executionContextMock)).getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());

    }

    @Test
    @SneakyThrows
    void regeneratePDFErrorReceiptGenerationException() {
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);

        doReturn(bizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        when(generateReceiptPdfServiceMock.generateReceipts(any(), any(), any())).thenAnswer(invocationOnMock -> {
            throw new Exception();
        });

        RegenerateReceiptRequest regenerateReceiptRequest = new RegenerateReceiptRequest();
        regenerateReceiptRequest.setEventId("1");

        HttpRequestMessage<Optional<RegenerateReceiptRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(regenerateReceiptRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(500,assertDoesNotThrow(() -> sut.run(request, executionContextMock)).getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
    }



    private Receipt buildReceiptWithStatus(ReceiptStatusType receiptStatusType, int numRetry) {
        return Receipt.builder()
                .eventData(EventData.builder()
                        .debtorFiscalCode("cd debtor")
                        .payerFiscalCode("cf payer")
                        .build())
                .eventId("biz-event-id")
                .status(receiptStatusType)
                .numRetry(numRetry)
                .generated_at(ORIGINAL_GENERATED_AT)
                .inserted_at(0L)
                .notified_at(0L)
                .build();
    }
}
