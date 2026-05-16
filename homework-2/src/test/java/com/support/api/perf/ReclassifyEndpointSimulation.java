package com.support.api.perf;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class ReclassifyEndpointSimulation extends Simulation {

    private final ScenarioBuilder scn = scenario("Reclassify endpoint")
            .exec(session -> session.set("body",
                    PerfConfig.createTicketBody("RC-" + ThreadLocalRandom.current().nextInt(1_000_000))))
            .exec(http("seed POST /tickets")
                    .post("/tickets")
                    .body(StringBody("#{body}"))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("id")))
            .exec(http("POST /tickets/{id}/auto-classify")
                    .post("/tickets/#{id}/auto-classify")
                    .check(status().is(200))
                    .check(jsonPath("$.confidence").exists()));

    {
        setUp(scn.injectOpen(
                rampUsersPerSec(5).to(25).during(30)
        )).protocols(PerfConfig.httpProtocol())
                .assertions(global().responseTime().percentile3().lt(1000),
                        global().failedRequests().percent().lt(1.0));
    }
}
