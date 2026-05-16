package com.support.api.dto;

import com.support.api.model.Ticket;
import com.support.api.model.TicketCategory;
import com.support.api.model.TicketMetadata;
import com.support.api.model.TicketPriority;
import com.support.api.model.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private UUID id;
    private String customerId;
    private String customerEmail;
    private String customerName;
    private String subject;
    private String description;
    private TicketCategory category;
    private TicketPriority priority;
    private TicketStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;
    private String assignedTo;
    private List<String> tags;
    private TicketMetadata metadata;

    public static TicketResponse from(Ticket t) {
        return TicketResponse.builder()
                .id(t.getId())
                .customerId(t.getCustomerId())
                .customerEmail(t.getCustomerEmail())
                .customerName(t.getCustomerName())
                .subject(t.getSubject())
                .description(t.getDescription())
                .category(t.getCategory())
                .priority(t.getPriority())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .resolvedAt(t.getResolvedAt())
                .assignedTo(t.getAssignedTo())
                .tags(t.getTags())
                .metadata(t.getMetadata())
                .build();
    }
}