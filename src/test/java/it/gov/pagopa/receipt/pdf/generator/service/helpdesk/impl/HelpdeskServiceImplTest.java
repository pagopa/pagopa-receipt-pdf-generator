package it.gov.pagopa.receipt.pdf.generator.service.helpdesk.impl;

import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.generator.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.generator.service.helpdesk.PDVTokenizerServiceRetryWrapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static it.gov.pagopa.receipt.pdf.generator.utils.ObjectMapperUtilsTest.getBizEventFromFile;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HelpdeskServiceImplTest {

    @Mock
    private PDVTokenizerServiceRetryWrapper pdvTokenizerServiceMock;

    @InjectMocks
    private HelpdeskServiceImpl sut;

    @Test
    @SneakyThrows
    void createReceiptSuccess() {
        BizEvent bizEvent = getBizEventFromFile("biz-events/biz-event.json");

        doReturn("debtor-cf-tokenized")
                .doReturn("payer-cf-tokenized")
                .when(pdvTokenizerServiceMock).generateTokenForFiscalCodeWithRetry(anyString());

        Receipt result = assertDoesNotThrow(() -> sut.createReceipt(bizEvent));

        assertNotNull(result);
        assertEquals(bizEvent.getId(), result.getEventId());
        assertNotNull(result.getEventData());
        assertNotNull(result.getEventData().getDebtorFiscalCode());
        assertNotNull(result.getEventData().getPayerFiscalCode());
        assertNotNull(result.getEventData().getTransactionCreationDate());
        assertNotNull(result.getEventData().getCart());
        assertFalse(result.getEventData().getCart().isEmpty());
        assertNotNull(result.getEventData().getCart().get(0).getSubject());
        assertNotNull(result.getEventData().getCart().get(0).getPayeeName());

        verify(pdvTokenizerServiceMock, times(2)).generateTokenForFiscalCodeWithRetry(anyString());
    }

    @Test
    @SneakyThrows
    void createReceiptFailOnDebtorTokenizer() {
        BizEvent bizEvent = getBizEventFromFile("biz-events/biz-event.json");

        doThrow(PDVTokenizerException.class)
                .when(pdvTokenizerServiceMock).generateTokenForFiscalCodeWithRetry(anyString());

        Receipt result = assertDoesNotThrow(() -> sut.createReceipt(bizEvent));

        assertNotNull(result);
        assertEquals(bizEvent.getId(), result.getEventId());
        assertEquals(ReceiptStatusType.FAILED, result.getStatus());
        assertNull(result.getEventData());

        verify(pdvTokenizerServiceMock).generateTokenForFiscalCodeWithRetry(anyString());
    }

    @Test
    @SneakyThrows
    void createReceiptFailOnPayerTokenizer() {
        BizEvent bizEvent = getBizEventFromFile("biz-events/biz-event.json");

        doReturn("debtor-cf-tokenized")
                .doThrow(PDVTokenizerException.class)
                .when(pdvTokenizerServiceMock).generateTokenForFiscalCodeWithRetry(anyString());

        Receipt result = assertDoesNotThrow(() -> sut.createReceipt(bizEvent));

        assertNotNull(result);
        assertEquals(bizEvent.getId(), result.getEventId());
        assertEquals(ReceiptStatusType.FAILED, result.getStatus());
        assertNull(result.getEventData());

        verify(pdvTokenizerServiceMock, times(2)).generateTokenForFiscalCodeWithRetry(anyString());
    }
}