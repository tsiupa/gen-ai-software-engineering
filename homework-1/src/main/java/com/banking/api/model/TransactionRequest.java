package com.banking.api.model;

import com.banking.api.validation.ValidCurrency;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionRequest {

    @Pattern(regexp = "ACC-[A-Z0-9]{5}", message = "Account number must match format ACC-XXXXX (5 alphanumeric characters)")
    private String fromAccount;

    @Pattern(regexp = "ACC-[A-Z0-9]{5}", message = "Account number must match format ACC-XXXXX (5 alphanumeric characters)")
    private String toAccount;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be a positive number")
    @Digits(integer = 15, fraction = 2, message = "Amount must have at most 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    @ValidCurrency
    private String currency;

    @NotNull(message = "Type is required")
    private Transaction.Type type;
}