package com.support.api.dto;

import com.support.api.model.TicketCategory;
import com.support.api.model.TicketPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationResult {
    private TicketCategory category;
    private TicketPriority priority;
    private double confidence;
    private String reasoning;
    private List<String> keywordsFound;
}