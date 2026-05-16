package com.support.api.perf;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class CrudThroughputSimulation extends Simulation {

    private final ScenarioBuilder scn = scenario("CRUD throughput")
            .exec(session -> {
                String cust = "PERF-CRUD-" + ThreadLocalRandom.current().nextInt(1_000_000);
                return session.set("customerId", cust).set("body", PerfConfig.createTicketBody(cust));
            })
            .exec(http("POST /tickets")
                    .post("/tickets")
                    .body(StringBody("#{body}"))
                    .check(status().is(201))
                    .check(io.gatling.javaapi.core.CoreDsl.jsonPath("$.id").saveAs("id")))
            .exec(http("GET /tickets/{id}")
                    .get("/tickets/#{id}")
                    .check(status().is(200)))
            .exec(http("PUT /tickets/{id}")
                    .put("/tickets/#{id}")
                    .body(StringBody(session -> PerfConfig.createTicketBody(session.getString("customerId"))))
                    .check(status().is(200)))
            .exec(http("DELETE /tickets/{id}")
                    .delete("/tickets/#{id}")
                    .check(status().is(204)));

    {
        setUp(scn.injectOpen(
                atOnceUsers(5),
                rampUsersPerSec(5).to(20).during(30)
        )).protocols(PerfConfig.httpProtocol())
                .assertions(global().responseTime().percentile3().lt(1500),
                        global().failedRequests().percent().lt(1.0));
    }
}
