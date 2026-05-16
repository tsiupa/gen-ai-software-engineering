package com.support.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.support.api.config.EnumConvertersConfig;
import com.support.api.dto.ClassificationResult;
import com.support.api.dto.ImportResult;
import com.support.api.dto.TicketFilter;
import com.support.api.dto.TicketRequest;
import com.support.api.exception.GlobalExceptionHandler;
import com.support.api.exception.TicketNotFoundException;
import com.support.api.model.Ticket;
import com.support.api.model.TicketCategory;
import com.support.api.model.TicketPriority;
import com.support.api.model.TicketStatus;
import com.support.api.service.TicketService;
import com.support.api.service.importer.ImportFormat;
import com.support.api.service.importer.TicketImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import({GlobalExceptionHandler.class, EnumConvertersConfig.class})
class TicketApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @MockBean
    TicketService ticketService;

    @MockBean
    TicketImportService importService;

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000abcd");

    private Ticket stubTicket() {
        return Ticket.builder()
                .id(ID)
                .customerId("C-1").customerEmail("a@b.com").customerName("A")
                .subject("subject").description("description here-long")
                .category(TicketCategory.OTHER).priority(TicketPriority.LOW)
                .status(TicketStatus.NEW)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private TicketRequest baseRequest() {
        return TicketRequest.builder()
                .customerId("C-1").customerEmail("a@b.com").customerName("A")
                .subject("subject").description("description here-long")
                .category(TicketCategory.OTHER).priority(TicketPriority.LOW)
                .build();
    }

    @Test
    @DisplayName("POST /tickets returns 201 with body")
    void postCreatesTicket() throws Exception {
        when(ticketService.create(any(), eq(false))).thenReturn(stubTicket());
        mvc.perform(post("/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(baseRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    @DisplayName("POST /tickets with invalid email returns 400")
    void postRejectsInvalidEmail() throws Exception {
        TicketRequest bad = baseRequest();
        bad.setCustomerEmail("not-an-email");
        mvc.perform(post("/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[?(@.field=='customerEmail')]").exists());
    }

    @Test
    @DisplayName("POST /tickets rejects short description and malformed JSON")
    void postRejectsShortDescription() throws Exception {
        TicketRequest bad = baseRequest();
        bad.setDescription("short");
        mvc.perform(post("/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("POST /tickets?auto_classify=true does not require category in body")
    void postAutoClassifyOmitsCategory() throws Exception {
        TicketRequest noCat = baseRequest();
        noCat.setCategory(null);
        noCat.setPriority(null);
        when(ticketService.create(any(), eq(true))).thenReturn(stubTicket());
        mvc.perform(post("/tickets?auto_classify=true").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(noCat)))
                .andExpect(status().isCreated());
        verify(ticketService).create(any(), eq(true));
    }

    @Test
    @DisplayName("GET /tickets returns a list")
    void getListsTickets() throws Exception {
        when(ticketService.list(any(TicketFilter.class))).thenReturn(List.of(stubTicket()));
        mvc.perform(get("/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ID.toString()));
    }

    @Test
    @DisplayName("GET /tickets filters by priority/category and rejects invalid enum values")
    void getListsWithFilter() throws Exception {
        ArgumentCaptor<TicketFilter> captor = ArgumentCaptor.forClass(TicketFilter.class);
        when(ticketService.list(captor.capture())).thenReturn(List.of());
        mvc.perform(get("/tickets?priority=high&category=billing_question&customer_id=C&assigned_to=agent"))
                .andExpect(status().isOk());
        TicketFilter f = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(f.getPriority()).isEqualTo(TicketPriority.HIGH);
        org.assertj.core.api.Assertions.assertThat(f.getCategory()).isEqualTo(TicketCategory.BILLING_QUESTION);
        org.assertj.core.api.Assertions.assertThat(f.getCustomerId()).isEqualTo("C");
        org.assertj.core.api.Assertions.assertThat(f.getAssignedTo()).isEqualTo("agent");

        mvc.perform(get("/tickets?priority=NOT_A_PRIORITY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("priority")));
    }

    @Test
    @DisplayName("GET /tickets/{id} returns the ticket")
    void getsById() throws Exception {
        when(ticketService.get(ID)).thenReturn(stubTicket());
        mvc.perform(get("/tickets/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer_id").value("C-1"));
    }

    @Test
    @DisplayName("GET /tickets/{id} returns 404 when missing")
    void getsMissing404() throws Exception {
        when(ticketService.get(ID)).thenThrow(new TicketNotFoundException(ID));
        mvc.perform(get("/tickets/" + ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /tickets/{id} updates and returns the ticket")
    void putUpdates() throws Exception {
        when(ticketService.update(eq(ID), any())).thenReturn(stubTicket());
        mvc.perform(put("/tickets/" + ID).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(baseRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    @DisplayName("DELETE /tickets/{id} returns 204 and 404 when missing")
    void deletesAndMissing() throws Exception {
        mvc.perform(delete("/tickets/" + ID))
                .andExpect(status().isNoContent());
        doThrow(new TicketNotFoundException(ID)).when(ticketService).delete(ID);
        mvc.perform(delete("/tickets/" + ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /tickets/{id}/auto-classify and /tickets/import (success + missing file + malformed)")
    void autoClassifyAndImport() throws Exception {
        ClassificationResult cr = ClassificationResult.builder()
                .category(TicketCategory.ACCOUNT_ACCESS)
                .priority(TicketPriority.URGENT)
                .confidence(0.8)
                .reasoning("matched login")
                .keywordsFound(List.of("login"))
                .build();
        when(ticketService.autoClassify(ID)).thenReturn(cr);
        mvc.perform(post("/tickets/" + ID + "/auto-classify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("account_access"))
                .andExpect(jsonPath("$.priority").value("urgent"))
                .andExpect(jsonPath("$.confidence").value(0.8));

        ImportResult ir = ImportResult.builder()
                .totalRecords(1).successful(1).failed(0)
                .createdIds(List.of(ID)).errors(List.of()).build();
        when(importService.importTickets(any(), eq(ImportFormat.JSON), anyBoolean())).thenReturn(ir);
        MockMultipartFile file = new MockMultipartFile("file", "x.json", "application/json", "[]".getBytes());
        mvc.perform(multipart("/tickets/import").file(file))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total_records").value(1));

        mvc.perform(multipart("/tickets/import"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("file")));

        MockMultipartFile mystery = new MockMultipartFile("file", "x.unknown", "application/octet-stream", "x".getBytes());
        mvc.perform(multipart("/tickets/import").file(mystery))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Import File"));
    }
}
