package com.support.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TicketCategory {
    ACCOUNT_ACCESS,
    TECHNICAL_ISSUE,
    BILLING_QUESTION,
    FEATURE_REQUEST,
    BUG_REPORT,
    OTHER;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static TicketCategory fromJson(String value) {
        if (value == null) {
            return null;
        }
        return TicketCategory.valueOf(value.trim().toUpperCase());
    }
}