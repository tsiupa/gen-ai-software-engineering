package com.banking.api.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class Transaction {

    public enum Type { deposit, withdrawal, transfer }
    public enum Status { pending, completed, failed }

    @Builder.Default
    private final String id = UUID.randomUUID().toString();

    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private String currency;
    private Type type;

    @Builder.Default
    private Instant timestamp = Instant.now();

    @Builder.Default
    private Status status = Status.completed;
}