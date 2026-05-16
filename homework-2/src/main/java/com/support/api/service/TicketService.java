package com.support.api.service;

import com.support.api.dto.TicketFilter;
import com.support.api.dto.TicketRequest;
import com.support.api.exception.TicketNotFoundException;
import com.support.api.model.Ticket;
import com.support.api.model.TicketStatus;
import com.support.api.repository.TicketRepository;
import com.support.api.repository.TicketSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository repository;

    public TicketService(TicketRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Ticket create(TicketRequest request) {
        Ticket ticket = Ticket.builder()
                .customerId(request.getCustomerId())
                .customerEmail(request.getCustomerEmail())
                .customerName(request.getCustomerName())
                .subject(request.getSubject())
                .description(request.getDescription())
                .category(request.getCategory())
                .priority(request.getPriority())
                .status(request.getStatus() != null ? request.getStatus() : TicketStatus.NEW)
                .assignedTo(request.getAssignedTo())
                .tags(request.getTags() != null ? new ArrayList<>(request.getTags()) : new ArrayList<>())
                .metadata(request.getMetadata())
                .build();
        return repository.save(ticket);
    }

    @Transactional(readOnly = true)
    public List<Ticket> list(TicketFilter filter) {
        return repository.findAll(TicketSpecifications.matching(filter));
    }

    @Transactional(readOnly = true)
    public Ticket get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new TicketNotFoundException(id));
    }

    @Transactional
    public Ticket update(UUID id, TicketRequest request) {
        Ticket ticket = repository.findById(id).orElseThrow(() -> new TicketNotFoundException(id));
        ticket.setCustomerId(request.getCustomerId());
        ticket.setCustomerEmail(request.getCustomerEmail());
        ticket.setCustomerName(request.getCustomerName());
        ticket.setSubject(request.getSubject());
        ticket.setDescription(request.getDescription());
        ticket.setCategory(request.getCategory());
        ticket.setPriority(request.getPriority());
        if (request.getStatus() != null) {
            TicketStatus prev = ticket.getStatus();
            ticket.setStatus(request.getStatus());
            if (request.getStatus() == TicketStatus.RESOLVED && prev != TicketStatus.RESOLVED) {
                ticket.setResolvedAt(Instant.now());
            }
        }
        ticket.setAssignedTo(request.getAssignedTo());
        ticket.setTags(request.getTags() != null ? new ArrayList<>(request.getTags()) : new ArrayList<>());
        ticket.setMetadata(request.getMetadata());
        return repository.save(ticket);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new TicketNotFoundException(id);
        }
        repository.deleteById(id);
    }
}