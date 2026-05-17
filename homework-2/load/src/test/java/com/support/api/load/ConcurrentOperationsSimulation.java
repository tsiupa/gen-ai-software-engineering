package com.support.api.load;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Concurrent Operations Load Test
 *
 * Covers the Task 5 requirement: "Concurrent operations (20+ simultaneous requests)".
 *
 * Two injection waves run in parallel:
 *
 *   Wave A – Burst CRUD (25 users at t=0)
 *     Each virtual user executes a full ticket lifecycle:
 *       POST /tickets  →  GET /tickets/{id}  →  PUT /tickets/{id}  →  DELETE /tickets/{id}
 *
 *   Wave B – Concurrent Reads (10 users at t=0)
 *     Each virtual user lists all tickets and queries by status, adding extra
 *     read concurrency on top of the write wave.
 *
 * Total simultaneous requests at t=0: 35 (25 CRUD + 10 read-only).
 *
 * Prerequisites: the application must be running on http://localhost:8080
 * (override with -Dapi.base-url=http://...).
 *
 * Run:
 *   mvn gatling:test -Dgatling.simulationClass=com.support.api.load.ConcurrentOperationsSimulation
 */
public class ConcurrentOperationsSimulation extends Simulation {

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    private static final String BASE_URL =
            System.getProperty("api.base-url", "http://localhost:8080");

    private static final int BURST_USERS = 25;
    private static final int READ_USERS  = 10;

    // -------------------------------------------------------------------------
    // Unique-ID counter shared across all virtual users
    // -------------------------------------------------------------------------

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    // -------------------------------------------------------------------------
    // HTTP protocol
    // -------------------------------------------------------------------------

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .shareConnections();

    // -------------------------------------------------------------------------
    // Feeder – generates a unique ticket payload per virtual user
    // -------------------------------------------------------------------------

    private static Iterator<Map<String, Object>> ticketFeeder() {
        return Stream.generate(() -> {
            int i = COUNTER.incrementAndGet();
            Map<String, Object> m = new HashMap<>();
            m.put("customerId",    "PERF-" + i);
            m.put("customerEmail", "perf" + i + "@loadtest.local");
            m.put("customerName",  "Perf User " + i);
            m.put("subject",       "Concurrent test ticket " + i);
            m.put("description",
                    "Load test ticket " + i + " created during concurrent operations performance test.");
            return m;
        }).iterator();
    }

    // -------------------------------------------------------------------------
    // Shared chain: full CRUD lifecycle executed by each burst user
    // -------------------------------------------------------------------------

    private static final ChainBuilder crudLifecycle =
            exec(
                    http("Create Ticket")
                            .post("/tickets")
                            .body(StringBody("""
                                    {
                                        "customer_id":    "#{customerId}",
                                        "customer_email": "#{customerEmail}",
                                        "customer_name":  "#{customerName}",
                                        "subject":        "#{subject}",
                                        "description":    "#{description}",
                                        "category":       "technical_issue",
                                        "priority":       "medium"
                                    }
                                    """))
                            .asJson()
                            .check(status().is(201))
                            .check(jsonPath("$.id").saveAs("ticketId"))
            )
            .exec(
                    http("Read Ticket")
                            .get("/tickets/#{ticketId}")
                            .check(status().is(200))
                            .check(jsonPath("$.customer_id").isEL("#{customerId}"))
            )
            .exec(
                    http("Update Ticket")
                            .put("/tickets/#{ticketId}")
                            .body(StringBody("""
                                    {
                                        "customer_id":    "#{customerId}",
                                        "customer_email": "#{customerEmail}",
                                        "customer_name":  "#{customerName}",
                                        "subject":        "#{subject}",
                                        "description":    "#{description}",
                                        "category":       "technical_issue",
                                        "priority":       "medium",
                                        "status":         "in_progress"
                                    }
                                    """))
                            .asJson()
                            .check(status().is(200))
                            .check(jsonPath("$.status").is("in_progress"))
            )
            .exec(
                    http("Delete Ticket")
                            .delete("/tickets/#{ticketId}")
                            .check(status().is(204))
            );

    // -------------------------------------------------------------------------
    // Scenarios
    // -------------------------------------------------------------------------

    /** Wave A: 25 users burst – each performs create → read → update → delete. */
    private final ScenarioBuilder burstCrudScenario =
            scenario("Wave A – Burst CRUD (25 concurrent users)")
                    .feed(ticketFeeder())
                    .exec(crudLifecycle);

    /** Wave B: 10 users burst – read-only list queries run in parallel with Wave A. */
    private final ScenarioBuilder concurrentReadScenario =
            scenario("Wave B – Concurrent Reads (10 concurrent users)")
                    .exec(
                            http("List All Tickets")
                                    .get("/tickets")
                                    .check(status().is(200))
                    )
                    .exec(
                            http("List New Tickets")
                                    .get("/tickets?status=new")
                                    .check(status().is(200))
                    )
                    .exec(
                            http("List In-Progress Tickets")
                                    .get("/tickets?status=in_progress")
                                    .check(status().is(200))
                    );

    // -------------------------------------------------------------------------
    // setUp – injection + assertions
    // -------------------------------------------------------------------------

    {
        setUp(
                // Wave A: 25 simultaneous CRUD users
                burstCrudScenario.injectOpen(
                        atOnceUsers(BURST_USERS)
                ),
                // Wave B: 10 simultaneous read users, overlapping Wave A from t=0
                concurrentReadScenario.injectOpen(
                        atOnceUsers(READ_USERS)
                )
        )
        .protocols(httpProtocol)
        .assertions(
                // At least 95 % of all requests must succeed
                global().successfulRequests().percent().gte(95.0),
                // 99th-percentile latency must stay under 5 s
                global().responseTime().percentile(99).lt(5_000),
                // Mean latency must stay under 1 s
                global().responseTime().mean().lt(1_000)
        );
    }
}
