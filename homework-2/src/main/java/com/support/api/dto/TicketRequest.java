package com.support.api.dto;

import com.support.api.model.TicketCategory;
import com.support.api.model.TicketMetadata;
import com.support.api.model.TicketPriority;
import com.support.api.model.TicketStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketRequest {

    @NotBlank(message = "customer_id is required")
    private String customerId;

    @NotBlank(message = "customer_email is required")
    @Email(message = "customer_email must be a valid email")
    private String customerEmail;

    @NotBlank(message = "customer_name is required")
    private String customerName;

    @NotBlank(message = "subject is required")
    @Size(min = 1, max = 200, message = "subject must be 1-200 characters")
    private String subject;

    @NotBlank(message = "description is required")
    @Size(min = 10, max = 2000, message = "description must be 10-2000 characters")
    private String description;

    private TicketCategory category;

    private TicketPriority priority;

    private TicketStatus status;

    private String assignedTo;

    private List<String> tags;

    @Valid
    private TicketMetadata metadata;
}