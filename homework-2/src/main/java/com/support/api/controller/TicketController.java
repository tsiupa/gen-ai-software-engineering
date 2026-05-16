package com.support.api.controller;

import com.support.api.dto.ClassificationResult;
import com.support.api.dto.ImportResult;
import com.support.api.dto.TicketFilter;
import com.support.api.dto.TicketRequest;
import com.support.api.dto.TicketResponse;
import com.support.api.model.Ticket;
import com.support.api.model.TicketCategory;
import com.support.api.model.TicketPriority;
import com.support.api.model.TicketStatus;
import com.support.api.service.TicketService;
import com.support.api.service.importer.ImportFormat;
import com.support.api.service.importer.TicketImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketImportService importService;

    public TicketController(TicketService ticketService, TicketImportService importService) {
        this.ticketService = ticketService;
        this.importService = importService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketRequest request,
                                                 @RequestParam(name = "auto_classify", defaultValue = "false") boolean autoClassify) {
        Ticket created = ticketService.create(request, autoClassify);
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.from(created));
    }

    @GetMapping
    public List<TicketResponse> list(@RequestParam(required = false) TicketCategory category,
                                     @RequestParam(required = false) TicketPriority priority,
                                     @RequestParam(required = false) TicketStatus status,
                                     @RequestParam(name = "customer_id", required = false) String customerId,
                                     @RequestParam(name = "assigned_to", required = false) String assignedTo) {
        TicketFilter filter = TicketFilter.builder()
                .category(category)
                .priority(priority)
                .status(status)
                .customerId(customerId)
                .assignedTo(assignedTo)
                .build();
        return ticketService.list(filter).stream()
                .map(TicketResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable UUID id) {
        return TicketResponse.from(ticketService.get(id));
    }

    @PutMapping("/{id}")
    public TicketResponse update(@PathVariable UUID id, @Valid @RequestBody TicketRequest request) {
        return TicketResponse.from(ticketService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ticketService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/auto-classify")
    public ClassificationResult autoClassify(@PathVariable UUID id) {
        return ticketService.autoClassify(id);
    }

    @PostMapping("/import")
    public ResponseEntity<ImportResult> bulkImport(@RequestPart("file") MultipartFile file,
                                                   @RequestParam(name = "format", required = false) String formatHint,
                                                   @RequestParam(name = "auto_classify", defaultValue = "false") boolean autoClassify)
            throws IOException {
        ImportFormat format = formatHint != null
                ? ImportFormat.valueOf(formatHint.trim().toUpperCase())
                : ImportFormat.detect(file.getOriginalFilename(), file.getContentType());
        ImportResult result = importService.importTickets(file.getInputStream(), format, autoClassify);
        HttpStatus status = result.getFailed() == 0 ? HttpStatus.CREATED : HttpStatus.MULTI_STATUS;
        return ResponseEntity.status(status).body(result);
    }
}