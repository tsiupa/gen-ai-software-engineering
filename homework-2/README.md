# Intelligent Customer Support Ticket Management API

A Spring Boot REST service that ingests support tickets from multiple file formats
(CSV / JSON / XML), auto-classifies them by category and priority using a
rule-based engine, and exposes a CRUD API backed by an in-memory H2 database.

Homework 2 of the **AI-Assisted Development** course.

---

## Features

- **CRUD API** for support tickets with filtering by category, priority, status, customer.
- **Bulk import** from CSV, JSON, and XML with per-record validation and partial-success reporting (HTTP `207 Multi-Status`).
- **Auto-classification** of category (`account_access`, `technical_issue`, `billing_question`, `feature_request`, `bug_report`, `other`) and priority (`urgent`, `high`, `medium`, `low`) — keyword-based, returns confidence + reasoning + matched keywords.
- **Snake_case JSON** payloads end-to-end (request and response).
- **In-memory H2** with the H2 console at `/h2-console`.
- **Test suite**: 56 unit + integration tests (JUnit 5, Mockito, REST Assured) at **93% line coverage** (JaCoCo).
- **Performance simulations**: 10 Gatling Java DSL simulations covering CRUD throughput, bulk import per format, classification under load.

---

## Architecture

```mermaid
flowchart LR
    Client["HTTP Client<br/>(curl / SDK / browser)"]
    subgraph App["Spring Boot Application"]
        Controller["TicketController<br/>(/tickets, /tickets/import,<br/>/tickets/{id}/auto-classify)"]
        ExceptionH["GlobalExceptionHandler"]
        TicketSvc["TicketService"]
        ImportSvc["TicketImportService"]
        Classifier["TicketClassifier<br/>(keyword rules)"]
        Parsers["Csv / Json / Xml<br/>TicketParser"]
        Repo["TicketRepository<br/>(Spring Data JPA + Specs)"]
    end
    H2[("H2 in-memory<br/>database")]

    Client -->|"HTTP/JSON"| Controller
    Controller --> ExceptionH
    Controller --> TicketSvc
    Controller --> ImportSvc
    TicketSvc --> Classifier
    TicketSvc --> Repo
    ImportSvc --> Parsers
    ImportSvc --> TicketSvc
    Repo --> H2
```

For deeper component descriptions, sequence diagrams, and design rationale see
[docs/ARCHITECTURE.md](ARCHITECTURE.md).

---

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Java 17 (built/tested under JDK 21 too) |
| Framework | Spring Boot 3.2.5 (Web, Data JPA, Validation) |
| Database | H2 (in-memory) |
| CSV / JSON / XML | OpenCSV, Jackson, Jackson-XML |
| Build | Maven |
| Tests | JUnit 5, Mockito, AssertJ, REST Assured |
| Coverage | JaCoCo (target >85%, currently 93%) |
| Performance | Gatling 3.10 (Java DSL) |
| Boilerplate | Lombok |

---

## Prerequisites

- JDK 17 or newer (project is built with Java 17 source level; JDK 21 works for runtime)
- Maven 3.9+

---

## Build and Run

```bash
# build a runnable jar (skips tests)
mvn -DskipTests package

# run the application (port 8080)
java -jar target/support-api-1.0.0.jar

# alternative: run via Maven plugin
mvn spring-boot:run
```

Once up, the service answers on `http://localhost:8080`. H2 console is available at
`http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:supportdb`, user `sa`, no password).

Quick smoke test:

```bash
curl -s -X POST "http://localhost:8080/tickets?auto_classify=true" \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "C-1",
    "customer_email": "alice@example.com",
    "customer_name": "Alice",
    "subject": "Cannot login",
    "description": "Cant access my account, this is critical."
  }'
```

See [docs/API_REFERENCE.md](API_REFERENCE.md) for every endpoint with examples.

---

## Running Tests

```bash
# unit + integration (REST Assured) tests
mvn test

# specific suite
mvn -Dtest='TicketApiTest' test
mvn -Dtest='TicketCrudIT' test

# coverage report: target/site/jacoco/index.html
mvn test && open target/site/jacoco/index.html
```

Performance simulations need a running server:

```bash
# in one terminal
java -jar target/support-api-1.0.0.jar

# in another
mvn gatling:test -Dgatling.simulationClass=com.support.api.perf.CrudThroughputSimulation
```

Full QA guide: [docs/TESTING_GUIDE.md](TESTING_GUIDE.md).

---

## Project Structure

```
homework-2/
├── README.md
├── API_REFERENCE.md
├── ARCHITECTURE.md
├── TESTING_GUIDE.md
├── TASKS.md
├── pom.xml
├── lombok.config
├── docs/
│   └── screenshots/
└── src/
    ├── main/
    │   ├── java/com/support/api/
    │   │   ├── SupportApiApplication.java
    │   │   ├── config/        # WebMvc converters
    │   │   ├── controller/    # REST controllers
    │   │   ├── dto/           # request/response/import DTOs
    │   │   ├── exception/     # @RestControllerAdvice + ErrorResponse
    │   │   ├── model/         # JPA entity, enums, embeddable
    │   │   ├── repository/    # Spring Data JPA + JpaSpecifications
    │   │   └── service/
    │   │       ├── classifier/  # TicketClassifier (rule-based)
    │   │       └── importer/    # Csv/Json/Xml parsers + dispatcher
    │   └── resources/
    │       └── application.properties
    └── test/
        ├── java/com/support/api/
        │   ├── controller/    # @WebMvcTest + Mockito
        │   ├── integration/   # REST Assured IT (Task 1 + Task 2)
        │   ├── model/         # entity + validation tests
        │   ├── perf/          # Gatling simulations (10)
        │   └── service/       # parser + classifier unit tests
        └── resources/fixtures/  # CSV/JSON/XML samples + malformed
```

---

## Documentation Map

| File | Audience | What's inside |
|---|---|---|
| `README.md` (this file) | Developers | Quickstart, architecture overview, run instructions |
| [`API_REFERENCE.md`](API_REFERENCE.md) | API consumers | Endpoints, schemas, errors, cURL examples |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Tech leads | Components, sequence diagrams, design decisions |
| [`TESTING_GUIDE.md`](TESTING_GUIDE.md) | QA engineers | Pyramid, fixtures, manual checklist, perf benchmarks |

---

<sub>This document was drafted with Claude Opus 4.7. See per-doc footers for the model behind each file.</sub>
