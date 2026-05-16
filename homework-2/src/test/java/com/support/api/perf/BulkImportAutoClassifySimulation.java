package com.support.api.perf;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.RawFileBodyPart;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class BulkImportAutoClassifySimulation extends Simulation {

    private final ScenarioBuilder scn = scenario("Bulk import with auto-classify")
            .exec(http("POST /tickets/import?auto_classify=true")
                    .post("/tickets/import?auto_classify=true")
                    .bodyPart(RawFileBodyPart("file", "fixtures/sample_tickets.json")
                            .contentType("application/json")).asMultipartForm()
                    .check(status().in(201, 207)));

    {
        setUp(scn.injectOpen(
                constantUsersPerSec(5).during(30)
        )).protocols(PerfConfig.httpProtocol())
                .assertions(global().responseTime().percentile3().lt(2500),
                        global().failedRequests().percent().lt(1.0));
    }
}
