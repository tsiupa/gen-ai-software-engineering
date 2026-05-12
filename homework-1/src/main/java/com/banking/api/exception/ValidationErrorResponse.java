package com.banking.api.exception;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ValidationErrorResponse {
    private String error;
    private List<FieldViolation> details;

    @Data
    @Builder
    public static class FieldViolation {
        private String field;
        private String message;
    }
}