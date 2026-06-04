package com.support.api.service.importer;

import com.support.api.dto.TicketRequest;
import com.support.api.exception.ImportFormatException;
import com.support.api.model.TicketCategory;
import com.support.api.model.TicketPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JsonImportTest {

    private JsonTicketParser parser;

    @BeforeEach
    void init() {
        parser = new JsonTicketParser();
    }

    @Test
    @DisplayName("Parses a valid JSON array")
    void parsesValidArray() {
        String json = """
                [
                  {"customer_id":"C1","customer_email":"a@b.com","customer_name":"A",
                   "subject":"subj","description":"description here-long",
                   "category":"account_access","priority":"urgent"}
                ]
                """;
        List<TicketRequest> out = parser.parse(stream(json));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getCategory()).isEqualTo(TicketCategory.ACCOUNT_ACCESS);
        assertThat(out.get(0).getPriority()).isEqualTo(TicketPriority.URGENT);
    }

    @Test
    @DisplayName("Snake_case fields map to camelCase Java")
    void mapsSnakeCaseFields() {
        String json = """
                [
                  {"customer_id":"C2","customer_email":"b@c.com","customer_name":"B",
                   "subject":"s","description":"description here-long",
                   "category":"other","priority":"medium","assigned_to":"agent-1"}
                ]
                """;
        TicketRequest r = parser.parse(stream(json)).get(0);
        assertThat(r.getCustomerId()).isEqualTo("C2");
        assertThat(r.getAssignedTo()).isEqualTo("agent-1");
    }

    @Test
    @DisplayName("Nested metadata is deserialized")
    void deserializesMetadata() {
        String json = """
                [
                  {"customer_id":"C3","customer_email":"c@d.com","customer_name":"C",
                   "subject":"s","description":"description here-long",
                   "category":"other","priority":"low",
                   "metadata":{"source":"chat","browser":"Edge","device_type":"tablet"}}
                ]
                """;
        TicketRequest r = parser.parse(stream(json)).get(0);
        assertThat(r.getMetadata()).isNotNull();
        assertThat(r.getMetadata().getBrowser()).isEqualTo("Edge");
    }

    @Test
    @DisplayName("Unknown fields are ignored")
    void ignoresUnknownFields() {
        String json = """
                [
                  {"customer_id":"C4","customer_email":"e@f.com","customer_name":"D",
                   "subject":"s","description":"description here-long",
                   "category":"other","priority":"low","unknown_extra_field":"ignored"}
                ]
                """;
        List<TicketRequest> out = parser.parse(stream(json));
        assertThat(out).hasSize(1);
    }

    @Test
    @DisplayName("Malformed JSON raises ImportFormatException")
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse(stream("{ this is not valid json }")))
                .isInstanceOf(ImportFormatException.class);
    }

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}