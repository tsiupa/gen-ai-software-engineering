package com.support.api.dto;

import com.support.api.model.TicketCategory;
import com.support.api.model.TicketPriority;
import com.support.api.model.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketFilter {
    private TicketCategory category;
    private TicketPriority priority;
    private TicketStatus status;
    private String customerId;
    private String assignedTo;
}