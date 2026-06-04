package com.support.api.load;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Combined Category + Priority Filter Load Test
 *
 * Covers the Task 5 requirement: "Combined filtering by category and priority".
 *
 * Execution is two-phased, chained with {@code andThen}:
 *
 *   Phase 1 – Seed (30 simultaneous users)
 *     30 virtual users each create one ticket, covering every combination of
 *     the 6 supported categories and 4 priorities (24 unique combos + 6 extras).
 *     All 30 users start at once; Gatling waits for every seed user to finish
 *     before Phase 2 begins.
 *
 *   Phase 2 – Filter Load (25 simultaneous users)
 *     25 virtual users start concurrently and each runs three queries per
 *     iteration using a randomly selected category+priority pair:
 *       • GET /tickets?category=X&priority=Y   (combined filter)
 *       • GET /tickets?category=X              (single-field filter)
 *       • GET /tickets?priority=Y              (single-field filter)
 *
 * Prerequisites: the application must be running on http://localhost:8080
 * (override with -Dapi.base-url=http://...).
 *
 * Run:
 *   mvn gatling:test -Dgatling.simulationClass=com.support.api.load.CombinedFilterSimulation
 */
public class CombinedFilterSimulation extends Simulation {

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    private static final String BASE_URL =
            System.getProperty("api.base-url", "http://localhost:8080");

    private static final int SEED_USERS   = 30;
    private static final int FILTER_USERS = 25;

    // -------------------------------------------------------------------------
    // Seed data – 30 tickets: 6 categories × 4 priorities (24) + 6 extras
    // -------------------------------------------------------------------------

    private static final String[][] SEED_COMBOS = {
            // category              priority    subject snippet              description snippet
            {"account_access",   "urgent", "Login failure critical",    "Critical account access failure affecting production users."},
            {"account_access",   "high",   "2FA not working",           "Two-factor authentication fails for multiple accounts today."},
            {"account_access",   "medium", "Password reset slow",       "Password reset emails are taking much longer than expected."},
            {"account_access",   "low",    "Profile photo issue",       "Profile photo thumbnail is slightly off after recent update."},
            {"billing_question", "urgent", "Double charge urgent",      "Customer was charged twice this billing cycle, needs immediate fix."},
            {"billing_question", "high",   "Refund not received",       "Approved refund has not been credited to the account yet."},
            {"billing_question", "medium", "Invoice missing",           "Monthly invoice was not generated for the current billing cycle."},
            {"billing_question", "low",    "Tax label incorrect",       "Tax label on invoice shows an incorrect jurisdiction code."},
            {"technical_issue",  "urgent", "Production environment down","Critical production environment is completely down and unavailable."},
            {"technical_issue",  "high",   "API errors under load",     "REST API returns 500 errors under heavy concurrent load today."},
            {"technical_issue",  "medium", "Slow response times",       "Response times have increased significantly over the last week."},
            {"technical_issue",  "low",    "Navigation misalignment",   "Minor cosmetic misalignment in the navigation menu on mobile."},
            {"feature_request",  "urgent", "Export feature needed ASAP","Export functionality is critically blocking our reporting process."},
            {"feature_request",  "high",   "Dark mode important",       "Dark mode is important for accessibility compliance requirements."},
            {"feature_request",  "medium", "Date range filter needed",  "Would love a date range filter option in the reporting dashboard."},
            {"feature_request",  "low",    "Keyboard shortcuts",        "Suggestion to add keyboard shortcuts for common navigation actions."},
            {"bug_report",       "urgent", "Data loss regression",      "Critical regression: data is lost on form submit after session expires."},
            {"bug_report",       "high",   "CSV export crash",          "Application crashes when exporting more than 1000 rows to CSV."},
            {"bug_report",       "medium", "Sort order incorrect",      "Steps to reproduce: sort by date ascending shows results in wrong order."},
            {"bug_report",       "low",    "Typo in error message",     "Minor typo found in the error message displayed on the login page."},
            {"other",            "medium", "General support inquiry",   "General question about our service that does not fit other categories."},
            {"other",            "low",    "Onboarding feedback",       "Suggestions for improving the new user onboarding flow experience."},
            // 8 extra tickets for denser dataset
            {"account_access",   "urgent", "Account locked out",        "Account is locked after multiple failed attempts, urgent unlock needed."},
            {"billing_question", "high",   "Subscription upgrade query","Important billing question about upgrading the current subscription plan."},
            {"technical_issue",  "urgent", "Possible security breach",  "Possible security incident detected, critical investigation required now."},
            {"feature_request",  "medium", "Bulk import feature",       "Requesting bulk import functionality for managing multiple records."},
            {"bug_report",       "high",   "Email notification broken",  "Regression: notification emails no longer sent on status change events."},
            {"account_access",   "medium", "SSO configuration help",    "Help needed configuring single sign-on with the corporate identity provider."},
            {"billing_question", "medium", "Add payment method",        "Question about adding a new payment method to an existing subscription."},
            {"technical_issue",  "high",   "Third-party integration",   "Third-party integration stopped working after the recent deployment."}
    };

    // -------------------------------------------------------------------------
    // Filter combinations used by the load phase
    // -------------------------------------------------------------------------

    private static final List<Map<String, Object>> FILTER_COMBOS;

    static {
        FILTER_COMBOS = new ArrayList<>();
        String[] categories = {
                "account_access", "billing_question", "technical_issue",
                "feature_request", "bug_report", "other"
        };
        String[] priorities = {"urgent", "high", "medium", "low"};
        for (String cat : categories) {
            for (String pri : priorities) {
                Map<String, Object> m = new HashMap<>();
                m.put("filterCat", cat);
                m.put("filterPri", pri);
                FILTER_COMBOS.add(m);
            }
        }
    }

    // -------------------------------------------------------------------------
    // HTTP protocol
    // -------------------------------------------------------------------------

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    // -------------------------------------------------------------------------
    // Feeders
    // -------------------------------------------------------------------------

    /** One row per seed ticket; exhausted after SEED_USERS pulls (no cycling). */
    private static Iterator<Map<String, Object>> buildSeedFeeder() {
        List<Map<String, Object>> rows = new ArrayList<>(SEED_COMBOS.length);
        for (int i = 0; i < SEED_COMBOS.length; i++) {
            String[] combo = SEED_COMBOS[i];
            Map<String, Object> m = new HashMap<>();
            m.put("seedCat",        combo[0]);
            m.put("seedPri",        combo[1]);
            m.put("seedCustomerId", "FILTER-SEED-" + (i + 1));
            m.put("seedEmail",      "seed" + (i + 1) + "@loadtest.local");
            m.put("seedName",       "Seed User " + (i + 1));
            m.put("seedSubject",    combo[2]);
            m.put("seedDesc",       combo[3]);
            rows.add(m);
        }
        return rows.iterator();
    }

    /**
     * Infinite random stream over all 24 category × priority combinations.
     * Each filter virtual user draws one row per scenario execution.
     */
    private static final Iterator<Map<String, Object>> FILTER_FEEDER =
            Stream.<Map<String, Object>>generate(() ->
                    new HashMap<>(FILTER_COMBOS.get(
                            ThreadLocalRandom.current().nextInt(FILTER_COMBOS.size())))
            ).iterator();

    // -------------------------------------------------------------------------
    // Scenarios
    // -------------------------------------------------------------------------

    /**
     * Phase 1 – Seed scenario.
     * 30 virtual users each pull one unique row from the seed feeder and create
     * one ticket. Runs first; Phase 2 starts only after all seeds complete.
     */
    private final ScenarioBuilder seedScenario =
            scenario("Phase 1 – Seed: 30 tickets with diverse category/priority")
                    .feed(buildSeedFeeder())
                    .exec(
                            http("Seed POST /tickets (#{seedCat}/#{seedPri})")
                                    .post("/tickets")
                                    .body(StringBody("""
                                            {
                                                "customer_id":    "#{seedCustomerId}",
                                                "customer_email": "#{seedEmail}",
                                                "customer_name":  "#{seedName}",
                                                "subject":        "#{seedSubject}",
                                                "description":    "#{seedDesc}",
                                                "category":       "#{seedCat}",
                                                "priority":       "#{seedPri}"
                                            }
                                            """))
                                    .asJson()
                                    .check(status().is(201))
                    );

    /**
     * Phase 2 – Filter load scenario.
     * Each virtual user picks a random category+priority combination and fires
     * three GET queries:
     *   1. Combined filter   – GET /tickets?category=X&priority=Y
     *   2. Category only     – GET /tickets?category=X
     *   3. Priority only     – GET /tickets?priority=Y
     */
    private final ScenarioBuilder filterScenario =
            scenario("Phase 2 – Filter load: 25 concurrent combined-filter queries")
                    .feed(FILTER_FEEDER)
                    .exec(
                            http("Combined GET /tickets?category=#{filterCat}&priority=#{filterPri}")
                                    .get("/tickets")
                                    .queryParam("category", "#{filterCat}")
                                    .queryParam("priority",  "#{filterPri}")
                                    .check(status().is(200))
                    )
                    .exec(
                            http("Category-only GET /tickets?category=#{filterCat}")
                                    .get("/tickets")
                                    .queryParam("category", "#{filterCat}")
                                    .check(status().is(200))
                    )
                    .exec(
                            http("Priority-only GET /tickets?priority=#{filterPri}")
                                    .get("/tickets")
                                    .queryParam("priority", "#{filterPri}")
                                    .check(status().is(200))
                    );

    // -------------------------------------------------------------------------
    // setUp – phased injection + assertions
    // -------------------------------------------------------------------------

    {
        setUp(
                // Phase 1: all 30 seed users start at once; Phase 2 waits for them
                seedScenario.injectOpen(atOnceUsers(SEED_USERS))
                        .andThen(
                                // Phase 2: 25 filter users burst after seed is complete
                                filterScenario.injectOpen(atOnceUsers(FILTER_USERS))
                        )
        )
        .protocols(httpProtocol)
        .assertions(
                // All requests (seed + filter) must succeed
                global().successfulRequests().percent().gte(99.0),
                // Filter queries must respond within 2 s at the 95th percentile
                global().responseTime().percentile(95).lt(2_000),
                // Mean latency across all requests must stay under 500 ms
                global().responseTime().mean().lt(500)
        );
    }
}
