package com.support.api.service.importer;

import com.support.api.dto.TicketRequest;
import com.support.api.exception.ImportFormatException;
import com.support.api.model.DeviceType;
import com.support.api.model.TicketCategory;
import com.support.api.model.TicketSource;
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
class CsvImportTest {

    private CsvTicketParser parser;

    @BeforeEach
    void init() {
        parser = new CsvTicketParser();
    }

    @Test
    @DisplayName("Parses a valid CSV with header row")
    void parsesValidCsv() {
        String csv = """
                customer_id,customer_email,customer_name,subject,description,category,priority
                C1,a@b.com,A,subj,description here-long,account_access,urgent
                C2,b@c.com,B,subj2,description here too,billing_question,low
                """;
        List<TicketRequest> out = parser.parse(stream(csv));
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getCustomerId()).isEqualTo("C1");
        assertThat(out.get(0).getCategory()).isEqualTo(TicketCategory.ACCOUNT_ACCESS);
    }

    @Test
    @DisplayName("Tags column splits on semicolons")
    void parsesSemicolonTags() {
        String csv = """
                customer_id,customer_email,customer_name,subject,description,category,priority,tags
                C1,a@b.com,A,subj,description here-long,other,medium,urgent;login;auth
                """;
        TicketRequest r = parser.parse(stream(csv)).get(0);
        assertThat(r.getTags()).containsExactly("urgent", "login", "auth");
    }

    @Test
    @DisplayName("Metadata columns populate TicketMetadata")
    void parsesMetadata() {
        String csv = """
                customer_id,customer_email,customer_name,subject,description,category,priority,source,browser,device_type
                C1,a@b.com,A,subj,description here-long,other,medium,web_form,Firefox,mobile
                """;
        TicketRequest r = parser.parse(stream(csv)).get(0);
        assertThat(r.getMetadata()).isNotNull();
        assertThat(r.getMetadata().getSource()).isEqualTo(TicketSource.WEB_FORM);
        assertThat(r.getMetadata().getBrowser()).isEqualTo("Firefox");
        assertThat(r.getMetadata().getDeviceType()).isEqualTo(DeviceType.MOBILE);
    }

    @Test
    @DisplayName("Missing optional fields are tolerated")
    void parsesWithMissingOptionalFields() {
        String csv = """
                customer_id,customer_email,customer_name,subject,description,category,priority
                C1,a@b.com,A,subj,description here-long,other,medium
                """;
        TicketRequest r = parser.parse(stream(csv)).get(0);
        assertThat(r.getTags()).isEmpty();
        assertThat(r.getMetadata()).isNull();
    }

    @Test
    @DisplayName("Invalid enum value raises ImportFormatException")
    void rejectsInvalidEnum() {
        String csv = """
                customer_id,customer_email,customer_name,subject,description,category,priority
                C1,a@b.com,A,subj,description here-long,NOT_A_CATEGORY,medium
                """;
        assertThatThrownBy(() -> parser.parse(stream(csv)))
                .isInstanceOf(ImportFormatException.class);
    }

    @Test
    @DisplayName("CSV with mismatched column counts raises ImportFormatException")
    void rejectsMalformedCsv() {
        String bad = """
                customer_id,customer_email,customer_name,subject,description,category,priority
                C1,a@b.com,A,subj,description here-long,extra,column,beyond,header,row
                """;
        assertThatThrownBy(() -> parser.parse(stream(bad)))
                .isInstanceOf(ImportFormatException.class);
    }

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}