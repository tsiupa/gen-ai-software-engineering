package com.banking.api.model;

import java.time.LocalDate;

public record TransactionFilter(
        String accountId,
        Transaction.Type type,
        LocalDate from,
        LocalDate to
) {}