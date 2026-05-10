package com.banking.api.repository;

import com.banking.api.model.Transaction;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TransactionRepository {

    private final Map<String, Transaction> store = new ConcurrentHashMap<>();

    public Transaction save(Transaction transaction) {
        store.put(transaction.getId(), transaction);
        return transaction;
    }

    public Collection<Transaction> findAll() {
        return store.values();
    }

    public Optional<Transaction> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
}