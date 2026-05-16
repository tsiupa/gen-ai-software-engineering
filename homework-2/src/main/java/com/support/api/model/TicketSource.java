package com.support.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TicketSource {
    WEB_FORM,
    EMAIL,
    API,
    CHAT,
    PHONE;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static TicketSource fromJson(String value) {
        if (value == null) {
            return null;
        }
        return TicketSource.valueOf(value.trim().toUpperCase());
    }
}