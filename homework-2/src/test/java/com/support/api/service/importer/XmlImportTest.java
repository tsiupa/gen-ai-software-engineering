package com.support.api.service.importer;

import com.support.api.dto.TicketRequest;
import com.support.api.exception.ImportFormatException;
import com.support.api.model.TicketCategory;
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
class XmlImportTest {

    private XmlTicketParser parser;

    @BeforeEach
    void init() {
        parser = new XmlTicketParser();
    }

    @Test
    @DisplayName("Parses multiple <ticket> elements")
    void parsesMultipleTickets() {
        String xml = """
                <?xml version="1.0"?>
                <tickets>
                  <ticket>
                    <customer_id>C1</customer_id><customer_email>a@b.com</customer_email>
                    <customer_name>A</customer_name><subject>s</subject>
                    <description>description here-long</description>
                    <category>account_access</category><priority>urgent</priority>
                  </ticket>
                  <ticket>
                    <customer_id>C2</customer_id><customer_email>b@c.com</customer_email>
                    <customer_name>B</customer_name><subject>s</subject>
                    <description>description here-long</description>
                    <category>billing_question</category><priority>low</priority>
                  </ticket>
                </tickets>
                """;
        List<TicketRequest> out = parser.parse(stream(xml));
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getCategory()).isEqualTo(TicketCategory.ACCOUNT_ACCESS);
    }

    @Test
    @DisplayName("Tags nested under <tags> are collected")
    void parsesNestedTags() {
        String xml = """
                <?xml version="1.0"?>
                <tickets>
                  <ticket>
                    <customer_id>C1</customer_id><customer_email>a@b.com</customer_email>
                    <customer_name>A</customer_name><subject>s</subject>
                    <description>description here-long</description>
                    <category>other</category><priority>low</priority>
                    <tags><tag>ui</tag><tag>enhancement</tag></tags>
                  </ticket>
                </tickets>
                """;
        TicketRequest r = parser.parse(stream(xml)).get(0);
        assertThat(r.getTags()).containsExactly("ui", "enhancement");
    }

    @Test
    @DisplayName("Metadata block is deserialized")
    void parsesMetadata() {
        String xml = """
                <?xml version="1.0"?>
                <tickets>
                  <ticket>
                    <customer_id>C1</customer_id><customer_email>a@b.com</customer_email>
                    <customer_name>A</customer_name><subject>s</subject>
                    <description>description here-long</description>
                    <category>other</category><priority>low</priority>
                    <metadata><source>web_form</source><browser>Firefox</browser><device_type>mobile</device_type></metadata>
                  </ticket>
                </tickets>
                """;
        TicketRequest r = parser.parse(stream(xml)).get(0);
        assertThat(r.getMetadata()).isNotNull();
        assertThat(r.getMetadata().getBrowser()).isEqualTo("Firefox");
    }

    @Test
    @DisplayName("Empty <tickets/> yields empty list")
    void parsesEmptyRoot() {
        String xml = "<?xml version=\"1.0\"?><tickets/>";
        List<TicketRequest> out = parser.parse(stream(xml));
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("Malformed XML raises ImportFormatException")
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> parser.parse(stream("not really xml<tickets>")))
                .isInstanceOf(ImportFormatException.class);
    }

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}