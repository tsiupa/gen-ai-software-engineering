package com.support.api.service.importer;

import com.support.api.dto.ImportError;
import com.support.api.dto.ImportResult;
import com.support.api.dto.TicketRequest;
import com.support.api.exception.ImportFormatException;
import com.support.api.model.Ticket;
import com.support.api.service.TicketService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TicketImportService {

    private final TicketService ticketService;
    private final CsvTicketParser csvParser;
    private final JsonTicketParser jsonParser;
    private final XmlTicketParser xmlParser;
    private final Validator validator;

    public TicketImportService(TicketService ticketService,
                               CsvTicketParser csvParser,
                               JsonTicketParser jsonParser,
                               XmlTicketParser xmlParser,
                               Validator validator) {
        this.ticketService = ticketService;
        this.csvParser = csvParser;
        this.jsonParser = jsonParser;
        this.xmlParser = xmlParser;
        this.validator = validator;
    }

    public ImportResult importTickets(InputStream input, ImportFormat format, boolean autoClassify) {
        List<TicketRequest> requests;
        try {
            requests = parserFor(format).parse(input);
        } catch (ImportFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportFormatException("Failed to parse import payload: " + e.getMessage(), e);
        }

        List<UUID> createdIds = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            TicketRequest req = requests.get(i);
            if (req == null) {
                errors.add(ImportError.builder()
                        .recordIndex(i)
                        .message("Record is null")
                        .build());
                continue;
            }
            Set<ConstraintViolation<TicketRequest>> violations = validator.validate(req);
            if (!violations.isEmpty()) {
                String msg = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .sorted()
                        .collect(Collectors.joining("; "));
                errors.add(ImportError.builder()
                        .recordIndex(i)
                        .message(msg)
                        .build());
                continue;
            }
            try {
                Ticket saved = ticketService.create(req, autoClassify);
                createdIds.add(saved.getId());
            } catch (Exception e) {
                errors.add(ImportError.builder()
                        .recordIndex(i)
                        .message(e.getMessage())
                        .build());
            }
        }

        return ImportResult.builder()
                .totalRecords(requests.size())
                .successful(createdIds.size())
                .failed(errors.size())
                .createdIds(createdIds)
                .errors(errors)
                .build();
    }

    public ImportResult importTickets(InputStream input, String filename, String contentType, boolean autoClassify) throws IOException {
        return importTickets(input, ImportFormat.detect(filename, contentType), autoClassify);
    }

    private TicketImportParser parserFor(ImportFormat format) {
        return switch (format) {
            case CSV -> csvParser;
            case JSON -> jsonParser;
            case XML -> xmlParser;
        };
    }
}