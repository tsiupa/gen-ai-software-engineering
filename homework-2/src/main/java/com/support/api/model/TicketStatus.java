package com.support.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TicketStatus {
    NEW,
    IN_PROGRESS,
    WAITING_CUSTOMER,
    RESOLVED,
    CLOSED;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static TicketStatus fromJson(String value) {
        if (value == null) {
            return null;
        }
        return TicketStatus.valueOf(value.trim().toUpperCase());
    }
}