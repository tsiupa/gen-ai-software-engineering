package com.support.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResult {
    private int totalRecords;
    private int successful;
    private int failed;
    private List<UUID> createdIds;
    private List<ImportError> errors;
}