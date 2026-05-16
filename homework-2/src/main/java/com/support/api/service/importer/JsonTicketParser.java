package com.support.api.service.importer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.support.api.dto.TicketRequest;
import com.support.api.exception.ImportFormatException;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class JsonTicketParser implements TicketImportParser {

    private final ObjectMapper mapper;

    public JsonTicketParser() {
        this.mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public List<TicketRequest> parse(InputStream input) {
        try {
            return mapper.readValue(input, new TypeReference<List<TicketRequest>>() {});
        } catch (Exception e) {
            throw new ImportFormatException("Malformed JSON file: " + e.getMessage(), e);
        }
    }
}