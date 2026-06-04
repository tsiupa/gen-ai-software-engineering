package com.support.api.service.importer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.support.api.dto.TicketRequest;
import com.support.api.exception.ImportFormatException;
import com.support.api.model.TicketCategory;
import com.support.api.model.TicketMetadata;
import com.support.api.model.TicketPriority;
import com.support.api.model.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class XmlTicketParser implements TicketImportParser {

    private final XmlMapper mapper;

    public XmlTicketParser() {
        this.mapper = (XmlMapper) new XmlMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public List<TicketRequest> parse(InputStream input) {
        try {
            TicketsXml wrapper = mapper.readValue(input, TicketsXml.class);
            List<TicketRequest> out = new ArrayList<>();
            if (wrapper.getTickets() != null) {
                for (TicketXml t : wrapper.getTickets()) {
                    out.add(t.toRequest());
                }
            }
            return out;
        } catch (Exception e) {
            throw new ImportFormatException("Malformed XML file: " + e.getMessage(), e);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JacksonXmlRootElement(localName = "tickets")
    static class TicketsXml {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "ticket")
        private List<TicketXml> tickets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class TicketXml {
        @JsonProperty("customer_id")
        private String customerId;
        @JsonProperty("customer_email")
        private String customerEmail;
        @JsonProperty("customer_name")
        private String customerName;
        private String subject;
        private String description;
        private TicketCategory category;
        private TicketPriority priority;
        private TicketStatus status;
        @JsonProperty("assigned_to")
        private String assignedTo;

        @JacksonXmlElementWrapper(localName = "tags")
        @JacksonXmlProperty(localName = "tag")
        private List<String> tags;

        private TicketMetadata metadata;

        TicketRequest toRequest() {
            return TicketRequest.builder()
                    .customerId(customerId)
                    .customerEmail(customerEmail)
                    .customerName(customerName)
                    .subject(subject)
                    .description(description)
                    .category(category)
                    .priority(priority)
                    .status(status)
                    .assignedTo(assignedTo)
                    .tags(tags)
                    .metadata(metadata)
                    .build();
        }
    }
}