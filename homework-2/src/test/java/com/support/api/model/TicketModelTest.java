package com.support.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.support.api.dto.TicketRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketModelTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("Ticket.builder produces a populated entity")
    void builderPopulatesEntity() {
        Ticket t = Ticket.builder()
                .customerId("C-1")
                .customerEmail("a@b.com")
                .customerName("Alice")
                .subject("s")
                .description("d")
                .category(TicketCategory.OTHER)
                .priority(TicketPriority.LOW)
                .build();
        assertThat(t.getCustomerId()).isEqualTo("C-1");
        assertThat(t.getCategory()).isEqualTo(TicketCategory.OTHER);
        assertThat(t.getTags()).isEmpty();
    }

    @Test
    @DisplayName("@PrePersist assigns id, createdAt, updatedAt, default status")
    void prePersistAssignsDefaults() throws Exception {
        Ticket t = Ticket.builder()
                .customerId("C").customerEmail("a@b.com").customerName("A")
                .subject("s").description("description1234567")
                .category(TicketCategory.OTHER).priority(TicketPriority.LOW)
                .build();
        invokeLifecycle(t, "onCreate");
        assertThat(t.getId()).isNotNull();
        assertThat(t.getCreatedAt()).isNotNull();
        assertThat(t.getUpdatedAt()).isEqualTo(t.getCreatedAt());
        assertThat(t.getStatus()).isEqualTo(TicketStatus.NEW);
    }

    @Test
    @DisplayName("@PreUpdate updates updatedAt and sets resolvedAt when RESOLVED")
    void preUpdateSetsResolvedAt() throws Exception {
        Ticket t = Ticket.builder()
                .customerId("C").customerEmail("a@b.com").customerName("A")
                .subject("s").description("description1234567")
                .category(TicketCategory.OTHER).priority(TicketPriority.LOW)
                .status(TicketStatus.RESOLVED)
                .build();
        invokeLifecycle(t, "onCreate");
        Instant created = t.getUpdatedAt();
        Thread.sleep(5);
        invokeLifecycle(t, "onUpdate");
        assertThat(t.getUpdatedAt()).isAfter(created);
        assertThat(t.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("TicketCategory.fromJson is case-insensitive")
    void categoryFromJsonCaseInsensitive() throws Exception {
        ObjectMapper m = new ObjectMapper();
        assertThat(m.readValue("\"BILLING_QUESTION\"", TicketCategory.class))
                .isEqualTo(TicketCategory.BILLING_QUESTION);
        assertThat(m.readValue("\"billing_question\"", TicketCategory.class))
                .isEqualTo(TicketCategory.BILLING_QUESTION);
    }

    @Test
    @DisplayName("TicketPriority.toJson serializes as lowercase")
    void priorityToJsonLowercase() throws Exception {
        ObjectMapper m = new ObjectMapper();
        assertThat(m.writeValueAsString(TicketPriority.URGENT)).isEqualTo("\"urgent\"");
        assertThat(m.writeValueAsString(TicketPriority.MEDIUM)).isEqualTo("\"medium\"");
    }

    @Test
    @DisplayName("TicketStatus.fromJson rejects unknown values")
    void statusFromJsonRejectsUnknown() {
        ObjectMapper m = new ObjectMapper();
        assertThatThrownBy(() -> m.readValue("\"NOT_A_STATUS\"", TicketStatus.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("TicketRequest validation rejects invalid email")
    void requestRejectsInvalidEmail() {
        TicketRequest r = base().customerEmail("not-an-email").build();
        Set<ConstraintViolation<TicketRequest>> v = validator.validate(r);
        assertThat(v).anyMatch(c -> c.getPropertyPath().toString().equals("customerEmail"));
    }

    @Test
    @DisplayName("TicketRequest validation rejects subject longer than 200 chars")
    void requestRejectsSubjectTooLong() {
        TicketRequest r = base().subject("x".repeat(201)).build();
        Set<ConstraintViolation<TicketRequest>> v = validator.validate(r);
        assertThat(v).anyMatch(c -> c.getPropertyPath().toString().equals("subject"));
    }

    @Test
    @DisplayName("TicketRequest validation rejects description shorter than 10 chars")
    void requestRejectsDescriptionTooShort() {
        TicketRequest r = base().description("short").build();
        Set<ConstraintViolation<TicketRequest>> v = validator.validate(r);
        assertThat(v).anyMatch(c -> c.getPropertyPath().toString().equals("description"));
    }

    private TicketRequest.TicketRequestBuilder base() {
        return TicketRequest.builder()
                .customerId("C-1")
                .customerEmail("a@b.com")
                .customerName("Alice")
                .subject("subject")
                .description("description text long enough")
                .category(TicketCategory.OTHER)
                .priority(TicketPriority.LOW);
    }

    private void invokeLifecycle(Ticket t, String name) throws Exception {
        Method m = Ticket.class.getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(t);
    }

    @SuppressWarnings("unused")
    private static void quietJacksonNamingHint() {
        new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}