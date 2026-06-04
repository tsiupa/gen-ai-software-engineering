package com.support.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TicketPriority {
    URGENT,
    HIGH,
    MEDIUM,
    LOW;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static TicketPriority fromJson(String value) {
        if (value == null) {
            return null;
        }
        return TicketPriority.valueOf(value.trim().toUpperCase());
    }
}