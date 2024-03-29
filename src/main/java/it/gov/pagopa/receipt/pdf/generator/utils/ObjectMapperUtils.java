package it.gov.pagopa.receipt.pdf.generator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.receipt.pdf.generator.entity.event.BizEvent;

import java.util.List;

public class ObjectMapperUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Hide from public usage.
     */
    private ObjectMapperUtils() {
    }

    /**
     * Encodes an object to a string
     *
     * @param value Object to be encoded
     * @return encoded string
     */
    public static String writeValueAsString(Object value) throws JsonProcessingException {
            return objectMapper.writeValueAsString(value);
    }

    /**
     * Maps string to object of defined Class
     *
     * @param string   String to map
     * @param outClass Class to be mapped to
     * @param <T>      Defined Class
     * @return object of the defined Class
     */
    public static <T> T mapString(final String string, Class<T> outClass) throws JsonProcessingException {
        return objectMapper.readValue(string, outClass);
    }

    /**
     * Maps string to object of defined Class
     *
     * @param string   String to map
     * @param outClass Class to be mapped to
     * @return object of the defined Class
     */
    public static List<BizEvent> mapBizEventListString(final String string, TypeReference<List<BizEvent>> outClass) throws JsonProcessingException {
        return objectMapper.readValue(string, outClass);
    }
}
