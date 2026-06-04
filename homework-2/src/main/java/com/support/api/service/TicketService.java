package com.support.api.service;

import com.support.api.dto.ClassificationResult;
import com.support.api.dto.TicketFilter;
import com.support.api.dto.TicketRequest;
import com.support.api.exception.TicketNotFoundException;
import com.support.api.model.Ticket;
import com.support.api.model.TicketStatus;
import com.support.api.repository.TicketRepository;
import com.support.api.repository.TicketSpecifications;
import com.support.api.service.classifier.TicketClassifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository repository;
    private final TicketClassifier classifier;

    public TicketService(TicketRepository repository, TicketClassifier classifier) {
        this.repository = repository;
        this.classifier = classifier;
    }

    @Transactional
    public Ticket create(TicketRequest request, boolean autoClassify) {
        ClassificationResult classification = null;
        if (autoClassify) {
            classification = classifier.classify(request.getSubject(), request.getDescription());
        } else {
            requireField("category", request.getCategory());
            requireField("priority", request.getPriority());
        }

        Ticket ticket = Ticket.builder()
                .customerId(request.getCustomerId())
                .customerEmail(request.getCustomerEmail())
                .customerName(request.getCustomerName())
                .subject(request.getSubject())
                .description(request.getDescription())
                .category(classification != null ? classification.getCategory() : request.getCategory())
                .priority(classification != null ? classification.getPriority() : request.getPriority())
                .status(request.getStatus() != null ? request.getStatus() : TicketStatus.NEW)
                .assignedTo(request.getAssignedTo())
                .classificationConfidence(classification != null ? classification.getConfidence() : null)
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
        requireField("category", request.getCategory());
        requireField("priority", request.getPriority());

        boolean categoryChanged = !request.getCategory().equals(ticket.getCategory());
        boolean priorityChanged = !request.getPriority().equals(ticket.getPriority());

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

        if (categoryChanged || priorityChanged) {
            ticket.setClassificationConfidence(null);
        }
        return repository.save(ticket);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new TicketNotFoundException(id);
        }
        repository.deleteById(id);
    }

    @Transactional
    public ClassificationResult autoClassify(UUID id) {
        Ticket ticket = repository.findById(id).orElseThrow(() -> new TicketNotFoundException(id));
        ClassificationResult result = classifier.classify(ticket.getSubject(), ticket.getDescription());
        ticket.setCategory(result.getCategory());
        ticket.setPriority(result.getPriority());
        ticket.setClassificationConfidence(result.getConfidence());
        repository.save(ticket);
        return result;
    }

    private static void requireField(String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required (omit only when auto_classify=true)");
        }
    }
}