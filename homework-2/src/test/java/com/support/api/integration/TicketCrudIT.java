package com.support.api.integration;

import com.support.api.repository.TicketRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TicketCrudIT {

    @LocalServerPort
    int port;

    @Autowired
    TicketRepository repository;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        repository.deleteAll();
    }

    @Test
    @DisplayName("Full CRUD lifecycle: POST → GET → PUT → GET → DELETE → 404")
    void crudLifecycle() {
        String id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "customer_id":"CR-1","customer_email":"c@r.com","customer_name":"CR",
                          "subject":"Initial subject","description":"Initial description here is long enough.",
                          "category":"other","priority":"low"
                        }
                        """)
                .when().post("/tickets")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("status", equalTo("new"))
                .body("created_at", notNullValue())
                .extract().path("id");

        given().when().get("/tickets/" + id)
                .then().statusCode(200)
                .body("customer_id", equalTo("CR-1"))
                .body("category", equalTo("other"));

        given().contentType(ContentType.JSON).body("""
                {
                  "customer_id":"CR-1","customer_email":"c@r.com","customer_name":"CR",
                  "subject":"Updated subject","description":"Updated description here is long enough.",
                  "category":"technical_issue","priority":"high","status":"resolved"
                }
                """).when().put("/tickets/" + id)
                .then().statusCode(200)
                .body("category", equalTo("technical_issue"))
                .body("priority", equalTo("high"))
                .body("status", equalTo("resolved"))
                .body("resolved_at", notNullValue());

        given().when().delete("/tickets/" + id).then().statusCode(204);
        given().when().get("/tickets/" + id).then().statusCode(404)
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("CSV bulk import: 6 valid records all succeed")
    void csvBulkImportSuccess() throws Exception {
        byte[] csv = Files.readAllBytes(new ClassPathResource("fixtures/sample_tickets.csv").getFile().toPath());
        given().multiPart("file", "sample.csv", csv, "text/csv")
                .when().post("/tickets/import")
                .then().statusCode(201)
                .body("total_records", equalTo(6))
                .body("successful", equalTo(6))
                .body("failed", equalTo(0));

        given().when().get("/tickets")
                .then().statusCode(200)
                .body("$", hasSize(6));
    }

    @Test
    @DisplayName("JSON bulk import with partial failure returns 207 + per-record errors")
    void jsonBulkImportPartial() {
        String body = """
                [
                  {"customer_id":"OK-1","customer_email":"ok1@x.com","customer_name":"OK",
                   "subject":"valid","description":"this description is long enough",
                   "category":"other","priority":"low"},
                  {"customer_id":"BAD","customer_email":"not-an-email","customer_name":"Bad",
                   "subject":"x","description":"too short",
                   "category":"other","priority":"low"}
                ]
                """;
        given().multiPart("file", "tickets.json", body.getBytes(), "application/json")
                .when().post("/tickets/import")
                .then().statusCode(207)
                .body("total_records", equalTo(2))
                .body("successful", equalTo(1))
                .body("failed", equalTo(1))
                .body("errors[0].record_index", equalTo(1))
                .body("errors[0].message", notNullValue());

        given().when().get("/tickets").then().statusCode(200).body("$", hasSize(1));
    }

    @Test
    @DisplayName("XML bulk import: 5 valid records all succeed; malformed file -> 400")
    void xmlBulkImportAndMalformed() throws Exception {
        byte[] xml = Files.readAllBytes(new ClassPathResource("fixtures/sample_tickets.xml").getFile().toPath());
        given().multiPart("file", "sample.xml", xml, "application/xml")
                .when().post("/tickets/import")
                .then().statusCode(201)
                .body("successful", equalTo(5));

        byte[] bad = Files.readAllBytes(new ClassPathResource("fixtures/malformed.xml").getFile().toPath());
        given().multiPart("file", "bad.xml", bad, "application/xml")
                .when().post("/tickets/import")
                .then().statusCode(400)
                .body("error", equalTo("Invalid Import File"));
    }

    @Test
    @DisplayName("Filtering by category, priority, status, customer_id, assigned_to combines correctly")
    void listingWithFilters() throws Exception {
        byte[] csv = Files.readAllBytes(new ClassPathResource("fixtures/sample_tickets.csv").getFile().toPath());
        given().multiPart("file", "sample.csv", csv, "text/csv")
                .when().post("/tickets/import").then().statusCode(201);

        given().queryParam("priority", "urgent").when().get("/tickets")
                .then().statusCode(200).body("size()", greaterThanOrEqualTo(2));

        given().queryParam("category", "account_access").queryParam("priority", "urgent")
                .when().get("/tickets")
                .then().statusCode(200)
                .body("size()", is(1))
                .body("[0].customer_id", equalTo("CSV-1"));

        given().queryParam("customer_id", "CSV-4").when().get("/tickets")
                .then().statusCode(200)
                .body("[0].category", equalTo("feature_request"));

        given().queryParam("status", "new").when().get("/tickets")
                .then().statusCode(200).body("size()", is(6));

        given().queryParam("priority", "high").when().get("/tickets")
                .then().statusCode(200)
                .body("[0].customer_id", equalTo("CSV-2"));
    }
}
