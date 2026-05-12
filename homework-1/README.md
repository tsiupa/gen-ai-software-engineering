# 🏦 Homework 1: Banking Transactions API

> **Student Name**: Oleksandr Tsiupa
> **Date Submitted**: 2026-05-10
> **AI Tools Used**: Claude Code

---

## Project Overview

A RESTful banking transactions API built with **Spring Boot 3.2** and **Java 17**. The API manages financial transactions between accounts using in-memory storage, with full validation, filtering, and CSV export capabilities.

---

## Features Implemented

### Task 1 — Core API
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/transactions` | Create a new transaction |
| `GET` | `/transactions` | List all transactions |
| `GET` | `/transactions/{id}` | Get a transaction by ID |
| `GET` | `/accounts/{accountId}/balance` | Get computed account balance |

### Task 2 — Validation
All fields on `POST /transactions` are validated before persistence:

- **Amount** — must be positive and have at most 2 decimal places (`@Positive`, `@Digits`)
- **Account numbers** — must match `ACC-XXXXX` (5 uppercase alphanumeric characters, e.g. `ACC-12345`)
- **Currency** — must be a valid ISO 4217 code (e.g. `USD`, `EUR`, `GBP`) via a custom `@ValidCurrency` annotation backed by `java.util.Currency`
- **Type** — must be one of `deposit`, `withdrawal`, `transfer`

Invalid requests return HTTP `400` with a structured body:
```json
{
  "error": "Validation failed",
  "details": [
    { "field": "amount", "message": "Amount must be a positive number" },
    { "field": "currency", "message": "Invalid currency code" }
  ]
}
```

### Task 3 — Transaction Filtering
`GET /transactions` supports optional query parameters that can be combined freely:

| Parameter | Example | Description |
|-----------|---------|-------------|
| `accountId` | `ACC-12345` | Matches transactions where the account is sender or receiver |
| `type` | `transfer` | Filters by transaction type |
| `from` | `2024-01-01` | Start of date range (ISO 8601, inclusive) |
| `to` | `2024-01-31` | End of date range (ISO 8601, inclusive) |

### Task 4 — CSV Export (Option C)
```
GET /transactions/export?format=csv
```
Exports all transactions as a downloadable CSV file (`Content-Disposition: attachment`).

Sample output:
```
id,fromAccount,toAccount,amount,currency,type,timestamp,status
a1b2c3...,ACC-12345,ACC-67890,100.50,USD,transfer,2024-01-15T10:30:00Z,completed
```

---

## Architecture Decisions

### In-memory Storage with `ConcurrentHashMap`
Transactions are stored in a `ConcurrentHashMap<String, Transaction>` inside `TransactionRepository`. This gives O(1) lookups by ID, thread safety without blocking reads, and zero infrastructure dependencies — matching the assignment requirement of no database.

### Layered Package Structure
```
com.banking.api/
├── controller/   — HTTP layer, request/response mapping
├── service/      — Business logic, filtering, balance calculation
├── repository/   — In-memory store, isolated behind an interface
├── model/        — Transaction, TransactionRequest, TransactionFilter
├── validation/   — @ValidCurrency annotation + ConstraintValidator
└── exception/    — @RestControllerAdvice for structured error responses
```

Each layer has a single responsibility. The controller never touches the store directly; the repository has no business logic.

### Custom Currency Validation
Rather than maintaining a hard-coded list of currency codes, `CurrencyValidator` delegates to `java.util.Currency.getInstance()`, which is backed by the JDK's built-in ISO 4217 data. This means it stays correct as the JDK is updated without any code changes.

### Balance Calculation
Account balance is computed on-the-fly from completed transactions rather than stored as a field. This avoids consistency issues (a stored balance can drift from the transaction log). The formula:

- **Credit**: `toAccount` matches and type is `deposit` or `transfer`
- **Debit**: `fromAccount` matches and type is `withdrawal` or `transfer`

### Filtering via Stream Predicates
`TransactionFilter` is a Java `record` passed from the controller to the service. Each non-null field adds a predicate to a stream chain — null fields are simply skipped. This avoids building query strings or overloaded method signatures.

---

## Transaction Model

```json
{
  "id": "uuid (auto-generated)",
  "fromAccount": "ACC-XXXXX",
  "toAccount": "ACC-XXXXX",
  "amount": 100.50,
  "currency": "USD",
  "type": "deposit | withdrawal | transfer",
  "timestamp": "2024-01-15T10:30:00Z",
  "status": "pending | completed | failed"
}
```

---

<div align="center">

*This project was completed as part of the AI-Assisted Development course.*

</div>
