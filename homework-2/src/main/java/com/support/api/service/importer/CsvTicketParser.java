package com.support.api.service.importer;

import com.opencsv.CSVReaderHeaderAware;
import com.support.api.dto.TicketRequest;
import com.support.api.exception.ImportFormatException;
import com.support.api.model.DeviceType;
import com.support.api.model.TicketCategory;
import com.support.api.model.TicketMetadata;
import com.support.api.model.TicketPriority;
import com.support.api.model.TicketSource;
import com.support.api.model.TicketStatus;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CsvTicketParser implements TicketImportParser {

    @Override
    public List<TicketRequest> parse(InputStream input) {
        List<TicketRequest> result = new ArrayList<>();
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            Map<String, String> row;
            while ((row = reader.readMap()) != null) {
                result.add(toRequest(row));
            }
        } catch (Exception e) {
            throw new ImportFormatException("Malformed CSV file: " + e.getMessage(), e);
        }
        return result;
    }

    private TicketRequest toRequest(Map<String, String> row) {
        return TicketRequest.builder()
                .customerId(trim(row.get("customer_id")))
                .customerEmail(trim(row.get("customer_email")))
                .customerName(trim(row.get("customer_name")))
                .subject(trim(row.get("subject")))
                .description(trim(row.get("description")))
                .category(parseEnum(row.get("category"), TicketCategory.class))
                .priority(parseEnum(row.get("priority"), TicketPriority.class))
                .status(parseEnum(row.get("status"), TicketStatus.class))
                .assignedTo(trim(row.get("assigned_to")))
                .tags(parseTags(row.get("tags")))
                .metadata(parseMetadata(row))
                .build();
    }

    private TicketMetadata parseMetadata(Map<String, String> row) {
        String source = row.get("source");
        String browser = row.get("browser");
        String device = row.get("device_type");
        if (isBlank(source) && isBlank(browser) && isBlank(device)) {
            return null;
        }
        return TicketMetadata.builder()
                .source(parseEnum(source, TicketSource.class))
                .browser(trim(browser))
                .deviceType(parseEnum(device, DeviceType.class))
                .build();
    }

    private List<String> parseTags(String raw) {
        if (isBlank(raw)) {
            return new ArrayList<>();
        }
        return Arrays.stream(raw.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(type.getSimpleName() + " has invalid value: " + value);
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}