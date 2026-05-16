package com.support.api.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class TicketMetadata {

    @Enumerated(EnumType.STRING)
    private TicketSource source;

    private String browser;

    @Enumerated(EnumType.STRING)
    private DeviceType deviceType;
}