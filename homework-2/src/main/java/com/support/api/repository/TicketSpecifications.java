package com.support.api.repository;

import com.support.api.dto.TicketFilter;
import com.support.api.model.Ticket;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> matching(TicketFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter == null) {
                return cb.conjunction();
            }
            if (filter.getCategory() != null) {
                predicates.add(cb.equal(root.get("category"), filter.getCategory()));
            }
            if (filter.getPriority() != null) {
                predicates.add(cb.equal(root.get("priority"), filter.getPriority()));
            }
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getCustomerId() != null && !filter.getCustomerId().isBlank()) {
                predicates.add(cb.equal(root.get("customerId"), filter.getCustomerId()));
            }
            if (filter.getAssignedTo() != null && !filter.getAssignedTo().isBlank()) {
                predicates.add(cb.equal(root.get("assignedTo"), filter.getAssignedTo()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}