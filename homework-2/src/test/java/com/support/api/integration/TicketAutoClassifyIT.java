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

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TicketAutoClassifyIT {

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
    @DisplayName("Full lifecycle: create with auto-classify, GET, DELETE")
    void fullLifecycleWithAutoClassify() {
        String id = given()
                .contentType(ContentType.JSON)
                .queryParam("auto_classify", true)
                .body("""
                        {
                          "customer_id":"IT-1","customer_email":"a@b.com","customer_name":"A",
                          "subject":"Cannot login","description":"I cant access my account; this is critical."
                        }
                        """)
                .when().post("/tickets")
                .then().statusCode(201)
                .body("category", equalTo("account_access"))
                .body("priority", equalTo("urgent"))
                .body("classification_confidence", greaterThan(0.0f))
                .extract().path("id");

        given().when().get("/tickets/" + id)
                .then().statusCode(200)
                .body("customer_id", equalTo("IT-1"));

        given().when().delete("/tickets/" + id).then().statusCode(204);
        given().when().get("/tickets/" + id).then().statusCode(404);
    }

    @Test
    @DisplayName("Bulk JSON import with auto_classify=true populates category and priority")
    void bulkImportWithAutoClassify() throws Exception {
        byte[] body = """
                [
                  {"customer_id":"IT-A","customer_email":"a@x.com","customer_name":"A",
                   "subject":"Refund","description":"Please refund my invoice - charged twice; this is important."},
                  {"customer_id":"IT-B","customer_email":"b@x.com","customer_name":"B",
                   "subject":"Dark mode","description":"would love a minor cosmetic dark mode suggestion."}
                ]
                """.getBytes();
        given()
                .multiPart("file", "tickets.json", body, "application/json")
                .queryParam("auto_classify", true)
                .when().post("/tickets/import")
                .then().statusCode(201)
                .body("successful", equalTo(2))
                .body("failed", equalTo(0));

        given().queryParam("customer_id", "IT-A").when().get("/tickets")
                .then().statusCode(200)
                .body("[0].category", equalTo("billing_question"))
                .body("[0].priority", equalTo("high"))
                .body("[0].classification_confidence", notNullValue());

        given().queryParam("customer_id", "IT-B").when().get("/tickets")
                .then().statusCode(200)
                .body("[0].category", equalTo("feature_request"))
                .body("[0].priority", equalTo("low"));
    }

    @Test
    @DisplayName("POST /tickets/{id}/auto-classify reclassifies an existing ticket")
    void reclassifyExisting() {
        String id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "customer_id":"IT-RC","customer_email":"r@c.com","customer_name":"R",
                          "subject":"Production is down",
                          "description":"Our production environment is down and a security incident may be ongoing.",
                          "category":"other","priority":"low"
                        }
                        """)
                .when().post("/tickets").then().statusCode(201).extract().path("id");

        given().when().post("/tickets/" + id + "/auto-classify")
                .then().statusCode(200)
                .body("priority", equalTo("urgent"))
                .body("keywords_found", hasItems("security"));

        given().when().get("/tickets/" + id)
                .then().statusCode(200)
                .body("priority", equalTo("urgent"))
                .body("classification_confidence", notNullValue());
    }

    @Test
    @DisplayName("Manual override via PUT clears classification_confidence")
    void manualOverrideClearsConfidence() {
        String id = given()
                .contentType(ContentType.JSON)
                .queryParam("auto_classify", true)
                .body("""
                        {
                          "customer_id":"IT-MO","customer_email":"m@o.com","customer_name":"M",
                          "subject":"Refund","description":"Please refund my invoice - this is important."
                        }
                        """)
                .when().post("/tickets").then().statusCode(201).extract().path("id");

        Float beforeConf = given().when().get("/tickets/" + id)
                .then().statusCode(200).extract().path("classification_confidence");
        assertThat(beforeConf).isNotNull().isGreaterThan(0.0f);

        given().contentType(ContentType.JSON).body("""
                {
                  "customer_id":"IT-MO","customer_email":"m@o.com","customer_name":"M",
                  "subject":"Refund","description":"Manual override description here-long.",
                  "category":"other","priority":"high"
                }
                """).when().put("/tickets/" + id).then().statusCode(200);

        given().when().get("/tickets/" + id)
                .then().statusCode(200)
                .body("category", equalTo("other"))
                .body("classification_confidence", nullValue());
    }

    @Test
    @DisplayName("Validation: POST without auto_classify and missing category returns 400")
    void validationWithoutAutoClassify() {
        given().contentType(ContentType.JSON).body("""
                {
                  "customer_id":"IT-V","customer_email":"v@x.com","customer_name":"V",
                  "subject":"hi","description":"this is a description"
                }
                """).when().post("/tickets")
                .then().statusCode(400)
                .body("message", org.hamcrest.Matchers.containsString("category"));

        given().when().get("/tickets").then().statusCode(200).body("$", empty());
    }
}
