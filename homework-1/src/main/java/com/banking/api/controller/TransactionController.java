package com.banking.api.controller;

import com.banking.api.model.Transaction;
import com.banking.api.model.TransactionFilter;
import com.banking.api.model.TransactionRequest;
import com.banking.api.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;

    @PostMapping("/transactions")
    public ResponseEntity<Transaction> create(@Valid @RequestBody TransactionRequest request) {
        Transaction created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> listAll(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) Transaction.Type type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.findAll(new TransactionFilter(accountId, type, from, to)));
    }

    @GetMapping("/transactions/export")
    public ResponseEntity<String> export(@RequestParam(defaultValue = "csv") String format) {
        if (!"csv".equalsIgnoreCase(format)) {
            return ResponseEntity.badRequest().body("Unsupported format: " + format + ". Only 'csv' is supported.");
        }
        String csv = service.exportAsCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transactions.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<Transaction> getById(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable String accountId) {
        BigDecimal balance = service.getBalance(accountId);
        return ResponseEntity.ok(Map.of("accountId", accountId, "balance", balance));
    }
}