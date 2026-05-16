package com.support.api.perf;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class CreateWithAutoClassifySimulation extends Simulation {

    private final ScenarioBuilder scn = scenario("Create + auto-classify")
            .exec(session -> session.set("body",
                    PerfConfig.autoClassifyBody("AC-" + ThreadLocalRandom.current().nextInt(1_000_000))))
            .exec(http("POST /tickets?auto_classify=true")
                    .post("/tickets?auto_classify=true")
                    .body(StringBody("#{body}"))
                    .check(status().is(201))
                    .check(io.gatling.javaapi.core.CoreDsl.jsonPath("$.classification_confidence").exists()));

    {
        setUp(scn.injectOpen(
                rampUsersPerSec(10).to(50).during(30)
        )).protocols(PerfConfig.httpProtocol())
                .assertions(global().responseTime().percentile3().lt(1000),
                        global().failedRequests().percent().lt(1.0));
    }
}
