package com.support.api.perf;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class MixedReadWriteSimulation extends Simulation {

    private final ScenarioBuilder scn = scenario("Mixed read/write under load")
            .exec(http("GET /tickets").get("/tickets").check(status().is(200)))
            .exec(http("GET /tickets?category=billing_question")
                    .get("/tickets?category=billing_question").check(status().is(200)))
            .exec(session -> session.set("body",
                    PerfConfig.autoClassifyBody("MX-" + ThreadLocalRandom.current().nextInt(1_000_000))))
            .exec(http("POST /tickets?auto_classify=true")
                    .post("/tickets?auto_classify=true")
                    .body(StringBody("#{body}"))
                    .check(status().is(201)));

    {
        setUp(scn.injectOpen(
                constantUsersPerSec(30).during(30)
        )).protocols(PerfConfig.httpProtocol())
                .assertions(global().responseTime().percentile3().lt(800),
                        global().failedRequests().percent().lt(1.0));
    }
}
