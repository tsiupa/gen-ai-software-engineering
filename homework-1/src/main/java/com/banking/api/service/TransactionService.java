package com.banking.api.service;

import com.banking.api.model.Transaction;
import com.banking.api.model.TransactionFilter;
import com.banking.api.model.TransactionRequest;
import com.banking.api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository repository;

    public Transaction create(TransactionRequest req) {
        Transaction transaction = Transaction.builder()
                .fromAccount(req.getFromAccount())
                .toAccount(req.getToAccount())
                .amount(req.getAmount())
                .currency(req.getCurrency())
                .type(req.getType())
                .build();
        return repository.save(transaction);
    }

    public List<Transaction> findAll(TransactionFilter filter) {
        var stream = repository.findAll().stream();

        if (filter.accountId() != null) {
            stream = stream.filter(t ->
                    filter.accountId().equals(t.getFromAccount()) ||
                    filter.accountId().equals(t.getToAccount()));
        }
        if (filter.type() != null) {
            stream = stream.filter(t -> t.getType() == filter.type());
        }
        if (filter.from() != null) {
            var fromInstant = filter.from().atStartOfDay(ZoneOffset.UTC).toInstant();
            stream = stream.filter(t -> !t.getTimestamp().isBefore(fromInstant));
        }
        if (filter.to() != null) {
            var toInstant = filter.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            stream = stream.filter(t -> t.getTimestamp().isBefore(toInstant));
        }

        return stream.toList();
    }

    public String exportAsCsv() {
        String header = "id,fromAccount,toAccount,amount,currency,type,timestamp,status";
        String rows = repository.findAll().stream()
                .map(t -> String.join(",",
                        t.getId(),
                        nullSafe(t.getFromAccount()),
                        nullSafe(t.getToAccount()),
                        t.getAmount().toPlainString(),
                        t.getCurrency(),
                        t.getType().name(),
                        t.getTimestamp().toString(),
                        t.getStatus().name()))
                .collect(Collectors.joining("\n"));
        return rows.isEmpty() ? header : header + "\n" + rows;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    public Optional<Transaction> findById(String id) {
        return repository.findById(id);
    }

    public BigDecimal getBalance(String accountId) {
        return repository.findAll().stream()
                .filter(t -> t.getStatus() == Transaction.Status.completed)
                .map(t -> {
                    boolean isCredit = accountId.equals(t.getToAccount())
                            && t.getType() != Transaction.Type.withdrawal;
                    boolean isDebit = accountId.equals(t.getFromAccount())
                            && t.getType() != Transaction.Type.deposit;
                    if (isCredit) return t.getAmount();
                    if (isDebit)  return t.getAmount().negate();
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}