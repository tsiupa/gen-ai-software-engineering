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

public class XmlBulkImportSimulation extends Simulation {

    private final ScenarioBuilder scn = scenario("XML bulk import")
            .exec(http("POST /tickets/import (XML)")
                    .post("/tickets/import")
                    .bodyPart(RawFileBodyPart("file", "fixtures/sample_tickets.xml")
                            .contentType("application/xml")).asMultipartForm()
                    .check(status().in(201, 207)));

    {
        setUp(scn.injectOpen(
                constantUsersPerSec(5).during(30)
        )).protocols(PerfConfig.httpProtocol())
                .assertions(global().responseTime().percentile3().lt(2000),
                        global().failedRequests().percent().lt(1.0));
    }
}
