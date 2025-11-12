package it.gov.pagopa.receipt.pdf.generator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.generator.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.generator.exception.BizEventNotValidException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReceiptGeneratorUtils {

    private static final String WORKING_DIRECTORY_PATH = System.getenv().getOrDefault("WORKING_DIRECTORY_PATH", "");
    private static final String PATTERN_FORMAT = "yyyy.MM.dd.HH.mm.ss";

    private ReceiptGeneratorUtils() {
    }

    public static String getReceiptEventReference(BizEvent bizEvent) {
        String receiptEventReference = null;

        if (bizEvent != null) {
            receiptEventReference = bizEvent.getId();
        }
        return receiptEventReference;
    }

    public static String getCartReceiptEventReference(BizEvent bizEvent) {
        String receiptEventReference = null;

        if (bizEvent != null && bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null) {
            receiptEventReference = String.valueOf(bizEvent.getTransactionDetails().getTransaction().getTransactionId());
        }
        return receiptEventReference;
    }


    public static List<BizEvent> getBizEventListFromMessage(
            String bizEventMessage, String functionName
    ) throws BizEventNotValidException {
        try {
            return ObjectMapperUtils.mapBizEventListString(bizEventMessage, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            String errorMsg = String.format("[%s] Error parsing the message coming from the queue", functionName);
            throw new BizEventNotValidException(errorMsg, e);
        }
    }

    public static Path createWorkingDirectory() throws IOException {
        File workingDirectory = new File(WORKING_DIRECTORY_PATH);
        if (!workingDirectory.exists()) {
            try {
                Files.createDirectory(workingDirectory.toPath());
            } catch (FileAlreadyExistsException ignored) {
                // The working directory already exist we don't need to create it
            }
        }
        return Files.createTempDirectory(workingDirectory.toPath(),
                DateTimeFormatter.ofPattern(PATTERN_FORMAT)
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.now()));
    }

    public static void deleteTempFolder(Path workingDirPath, Logger logger) {
        try {
            FileUtils.deleteDirectory(workingDirPath.toFile());
        } catch (IOException e) {
            logger.warn("Unable to clear working directory: {}", workingDirPath, e);
        }
    }

    public static boolean receiptAlreadyCreated(ReceiptMetadata receiptMetadata) {
        return receiptMetadata != null
                && receiptMetadata.getUrl() != null
                && receiptMetadata.getName() != null
                && !receiptMetadata.getUrl().isEmpty()
                && !receiptMetadata.getName().isEmpty();
    }
}
