# Testing Guide

> Audience: QA engineers. Covers the test pyramid, how to run each layer,
> where the fixtures live, a manual checklist for exploratory testing, and
> the performance benchmark targets used by the Gatling simulations.

---

## Test Pyramid

```mermaid
flowchart TB
    subgraph Pyr["Test pyramid"]
        direction TB
        Perf["Performance (Gatling)<br/>10 simulations - load only"]
        IT["Integration (REST Assured)<br/>10 tests - 2 IT classes"]
        Unit["Unit (JUnit 5 + Mockito)<br/>46 tests - 6 classes"]
    end
    Perf --> IT
    IT --> Unit

    classDef perfStyle fill:#ffe4c4,stroke:#cc7a00
    classDef itStyle fill:#cce5ff,stroke:#0050a0
    classDef unitStyle fill:#d4f0d4,stroke:#1a7d1a
    class Perf perfStyle
    class IT itStyle
    class Unit unitStyle
```

| Layer | Count | What runs | Speed |
|---|---|---|---|
| **Unit** | 46 | JUnit 5 + Mockito (and `@WebMvcTest` for the controller) | ~5–10 s total |
| **Integration** | 10 | REST Assured against `@SpringBootTest(RANDOM_PORT)` with real H2 | ~25–30 s per IT class |
| **Performance** | 10 simulations | Gatling 3.10 Java DSL, separate run goal | 30 s each (configurable) |

Total in-process tests: **56**. JaCoCo line coverage: **93%**, branch coverage: **76%**.

---

## How to run

### All unit + integration tests

```bash
cd homework-2
mvn test
```

Surefire is configured to pick up both `*Test.java` (unit) and `*IT.java` (integration).

### A specific test class

```bash
mvn -Dtest='TicketApiTest' test
mvn -Dtest='TicketCrudIT' test
mvn -Dtest='CategorizationTest#urgentPriority' test
```

### Coverage report

JaCoCo runs in the `test` phase. Open the HTML report after a run:

```bash
mvn test
open target/site/jacoco/index.html
```

The CSV/XML variants are at `target/site/jacoco/jacoco.csv` and `jacoco.xml`.

### Performance (Gatling)

Gatling needs a **running server** — it does not boot Spring Boot itself.

```bash
# terminal 1 — server
java -jar target/support-api-1.0.0.jar
# wait for "Started SupportApiApplication"

# terminal 2 — one simulation at a time
mvn gatling:test -Dgatling.simulationClass=com.support.api.perf.CrudThroughputSimulation
```

Results are written to `target/gatling/<sim>-<timestamp>/index.html`.

If your server runs on a non-default URL, pass `-Dperf.baseUrl=http://other-host:9000`.

---

## Test layout

```
src/test/java/com/support/api/
├── controller/
│   └── TicketApiTest.java          # 11 tests — @WebMvcTest + Mockito @MockBean
├── model/
│   └── TicketModelTest.java        # 9 tests — entity lifecycle, enums, validation
├── service/
│   ├── classifier/
│   │   └── CategorizationTest.java # 10 tests — every category + every priority
│   └── importer/
│       ├── CsvImportTest.java      # 6 tests
│       ├── JsonImportTest.java     # 5 tests
│       └── XmlImportTest.java      # 5 tests
├── integration/
│   ├── TicketCrudIT.java           # 5 IT — Task 1 (CRUD + bulk import + filtering)
│   └── TicketAutoClassifyIT.java   # 5 IT — Task 2 (auto-classify on create / import / endpoint)
└── perf/
    ├── PerfConfig.java
    ├── (5 Task 1 sims)             # CrudThroughput / Csv|Json|XmlBulkImport / FilteredList
    └── (5 Task 2 sims)             # CreateWithAutoClassify / BulkImportAutoClassify /
                                    # ReclassifyEndpoint / MixedReadWrite / ConcurrentClassify
```

### Fixtures

`src/test/resources/fixtures/`:

| File | Records | Used by |
|---|---|---|
| `sample_tickets.csv` | 6 valid rows covering every category | `TicketCrudIT.csvBulkImportSuccess`, `FilteredListSimulation`, Gatling CSV upload sim |
| `sample_tickets.json` | 5 valid records | `TicketCrudIT`, Gatling JSON sim, auto-classify sim |
| `sample_tickets.xml` | 5 valid records | `TicketCrudIT.xmlBulkImportAndMalformed`, Gatling XML sim |
| `malformed.csv` | (broken) | Negative test |
| `malformed.json` | (broken) | Negative test |
| `malformed.xml` | (broken) | `TicketCrudIT.xmlBulkImportAndMalformed` |

Inline fixtures (Java text blocks) are used inside unit tests where the data is small and tightly coupled to the assertion.

---

## Manual Testing Checklist

A 10-minute smoke run before submission or merging. Assume `http://localhost:8080`.

### CRUD basics
- [ ] `POST /tickets` returns **201** and the body has `id`, `created_at`, `updated_at`, default `status: "new"`.
- [ ] `POST /tickets` with `customer_email: "not-an-email"` returns **400** with `field_errors[].field == "customerEmail"`.
- [ ] `POST /tickets` with `description: "short"` returns **400**.
- [ ] `GET /tickets/{id}` returns **200** for an existing id, **404** for a fresh random UUID.
- [ ] `PUT /tickets/{id}` with `status: "resolved"` populates `resolved_at`.
- [ ] `DELETE /tickets/{id}` returns **204** then a subsequent `GET` returns **404**.

### Filtering
- [ ] `GET /tickets?priority=high` (lowercase) returns only high-priority tickets.
- [ ] `GET /tickets?priority=NOT_VALID` returns **400** with `"message": "...priority..."`.
- [ ] `GET /tickets?category=billing_question&customer_id=C-1` AND-combines the filters.

### Bulk import
- [ ] `POST /tickets/import` with `sample_tickets.csv` returns **201** and `successful: 6, failed: 0`.
- [ ] `POST /tickets/import` with a JSON payload containing one invalid row returns **207** with `errors[0].record_index`.
- [ ] `POST /tickets/import` with `malformed.xml` returns **400 Invalid Import File`.
- [ ] `POST /tickets/import` without the `file` part returns **400** with `"message": "...file..."`.

### Auto-classification
- [ ] `POST /tickets?auto_classify=true` with `subject: "Cannot login"`, `description: "cant access ... critical"` returns **201** with `category: "account_access"`, `priority: "urgent"`, and a numeric `classification_confidence`.
- [ ] `POST /tickets` without `auto_classify` and no `category` returns **400** with `"message": "...category..."`.
- [ ] `POST /tickets/{id}/auto-classify` on a "production environment is down ... security" ticket returns **200** with `priority: "urgent"`, `keywords_found` including `"security"`.
- [ ] `PUT /tickets/{id}` with a *different* category clears `classification_confidence` (next `GET` shows it as missing from the JSON).
- [ ] `PUT /tickets/{id}` with the *same* category/priority preserves `classification_confidence`.

### Imports + classifier together
- [ ] `POST /tickets/import?auto_classify=true` with `sample_tickets.json` returns **201** and every saved ticket has a non-null `classification_confidence`.

---

## Performance Benchmarks

The Gatling assertions in each simulation set the **acceptance thresholds**. If
any assertion fails the build will report it. All numbers below are 95th-percentile
response times against a local laptop run (Java 21, 8 cores, in-memory H2). Treat
them as ballparks; CI numbers will differ.

| Simulation | Pattern | Target p95 latency | Failure rate | Local measured p95* |
|---|---|---|---|---|
| `CrudThroughputSimulation` | atOnce(5) + ramp 5→20 rps for 30 s | < 1 500 ms | < 1 % | ~ 300 ms |
| `CsvBulkImportSimulation` | const 5 rps × 30 s, multipart CSV | < 2 000 ms | < 1 % | ~ 220 ms |
| `JsonBulkImportSimulation` | const 5 rps × 30 s, multipart JSON | < 2 000 ms | < 1 % | ~ 180 ms |
| `XmlBulkImportSimulation` | const 5 rps × 30 s, multipart XML | < 2 000 ms | < 1 % | ~ 230 ms |
| `FilteredListSimulation` | const 20 rps × 30 s, 5 filter combinations | < 500 ms | < 1 % | ~ 80 ms |
| `CreateWithAutoClassifySimulation` | ramp 10→50 rps × 30 s | < 1 000 ms | < 1 % | ~ 250 ms |
| `BulkImportAutoClassifySimulation` | const 5 rps × 30 s | < 2 500 ms | < 1 % | ~ 280 ms |
| `ReclassifyEndpointSimulation` | seed + ramp 5→25 rps × 30 s | < 1 000 ms | < 1 % | ~ 200 ms |
| `MixedReadWriteSimulation` | const 30 rps × 30 s (mixed GET/POST) | < 800 ms | < 1 % | ~ 180 ms |
| `ConcurrentClassifySimulation` | atOnceUsers(25) × repeat(5) | < 1 500 ms | < 1 % | ~ 350 ms |

<sub>* Indicative numbers from a local dev run, not committed CI baselines.</sub>

To tighten or loosen a threshold, edit the matching `Simulation` class — the
`.assertions(...)` block at the bottom is the contract.

---

## Tips and Gotchas

- **`mvn test` does not run Gatling.** Gatling is bound to its own plugin goal (`gatling:test`). This is intentional — perf runs need a live server and shouldn't gate CI on every push.
- **REST Assured IT classes share H2 in-memory state with the running app, but each test method clears the repository in `@BeforeEach`.** If you ever see a test pass in isolation and fail in a batch, check whether you added an integration test without resetting state.
- **`classification_confidence` is `null` after a manual category change.** Tests assert this via `body("classification_confidence", nullValue())` — Jackson omits `null` fields (`spring.jackson.default-property-inclusion=non_null`), so REST Assured's `nullValue()` matcher works on the missing-key case too.
- **JaCoCo will report 41 % if `lombok.config` is missing or out-of-date.** The file flips on `@Generated` annotations so the coverage agent skips Lombok-emitted methods. A clean rebuild may be needed (`mvn clean test`) after editing it.

---

<sub>This document was drafted with Claude Opus 4.7. Reach out to the team chat if a threshold above looks wrong on your hardware — they are tuned to a single laptop today.</sub>
