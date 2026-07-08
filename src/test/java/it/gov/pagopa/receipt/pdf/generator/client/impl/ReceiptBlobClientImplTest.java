package it.gov.pagopa.receipt.pdf.generator.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlockBlobItem;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.generator.model.response.BlobStorageResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

@ExtendWith(MockitoExtension.class)
class ReceiptBlobClientImplTest {

    @Mock
    private BlobContainerClient mockContainer;
    @Mock
    private BlobClient mockClient;
    @Mock
    private Response<BlockBlobItem> mockBlockItem;

    @InjectMocks
    private ReceiptBlobClientImpl sut;

    @Test
    void testSingleton() throws Exception {
        @SuppressWarnings("secrets:S6338")
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "RECEIPTS_STORAGE_CONN_STRING", "DefaultEndpointsProtocol=https;AccountName=samplestorage;AccountKey=" + mockKey + ";EndpointSuffix=core.windows.net"
        ).execute(() -> Assertions.assertDoesNotThrow(ReceiptBlobClientImpl::getInstance));
    }

    @Test
    void runOk() {
        String validBlobName = "a valid blob name";
        String validBlobUrl = "a valid blob url";

        when(mockContainer.getBlobClient(any())).thenReturn(mockClient);
        when(mockBlockItem.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(mockClient.uploadWithResponse(any(), eq(null), eq(null)))
                .thenReturn(mockBlockItem);
        when(mockClient.getBlobName()).thenReturn(validBlobName);
        when(mockClient.getBlobUrl()).thenReturn(validBlobUrl);

        BlobStorageResponse response = assertDoesNotThrow(
                () -> sut.savePdfToBlobStorage(InputStream.nullInputStream(), "filename"));

        assertEquals(HttpStatus.CREATED.value(), response.getStatusCode());
        assertEquals(validBlobName, response.getDocumentName());
        assertEquals(validBlobUrl, response.getDocumentUrl());

    }

    @Test
    void runKo() {
        when(mockContainer.getBlobClient(any())).thenReturn(mockClient);
        when(mockBlockItem.getStatusCode()).thenReturn(HttpStatus.NO_CONTENT.value());
        when(mockClient.uploadWithResponse(any(), eq(null), eq(null)))
                .thenReturn(mockBlockItem);

        BlobStorageResponse response = assertDoesNotThrow(
                () -> sut.savePdfToBlobStorage(InputStream.nullInputStream(), "filename"));

        assertEquals(HttpStatus.NO_CONTENT.value(), response.getStatusCode());
        assertNull(response.getDocumentName());
        assertNull(response.getDocumentUrl());
    }
}