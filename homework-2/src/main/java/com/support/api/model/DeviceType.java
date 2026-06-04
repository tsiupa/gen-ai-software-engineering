package com.support.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DeviceType {
    DESKTOP,
    MOBILE,
    TABLET;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static DeviceType fromJson(String value) {
        if (value == null) {
            return null;
        }
        return DeviceType.valueOf(value.trim().toUpperCase());
    }
}