package com.support.api.perf;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class FilteredListSimulation extends Simulation {

    private final ScenarioBuilder scn = scenario("Filtered listing")
            .exec(http("GET /tickets").get("/tickets").check(status().is(200)))
            .exec(http("GET /tickets?priority=high").get("/tickets?priority=high").check(status().is(200)))
            .exec(http("GET /tickets?category=billing_question").get("/tickets?category=billing_question").check(status().is(200)))
            .exec(http("GET /tickets?status=new").get("/tickets?status=new").check(status().is(200)))
            .exec(http("GET /tickets?priority=urgent&category=account_access")
                    .get("/tickets?priority=urgent&category=account_access").check(status().is(200)));

    {
        setUp(scn.injectOpen(
                constantUsersPerSec(20).during(30)
        )).protocols(PerfConfig.httpProtocol())
                .assertions(global().responseTime().percentile3().lt(500),
                        global().failedRequests().percent().lt(1.0));
    }
}
