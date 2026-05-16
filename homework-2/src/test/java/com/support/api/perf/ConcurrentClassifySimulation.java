package com.support.api.perf;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.repeat;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class ConcurrentClassifySimulation extends Simulation {

    private final ScenarioBuilder scn = scenario("Concurrent auto-classification burst")
            .repeat(5)
            .on(
                    exec(session -> session.set("body",
                            PerfConfig.autoClassifyBody("CC-" + ThreadLocalRandom.current().nextInt(1_000_000))))
                            .exec(http("POST /tickets?auto_classify=true")
                                    .post("/tickets?auto_classify=true")
                                    .body(StringBody("#{body}"))
                                    .check(status().is(201))
                                    .check(jsonPath("$.classification_confidence").exists()))
            );

    {
        setUp(scn.injectOpen(
                atOnceUsers(25)
        )).protocols(PerfConfig.httpProtocol())
                .assertions(global().responseTime().percentile3().lt(1500),
                        global().failedRequests().percent().lt(1.0));
    }
}
