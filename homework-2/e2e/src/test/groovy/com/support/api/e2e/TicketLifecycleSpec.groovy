package com.support.api.e2e

import io.restassured.http.ContentType
import spock.lang.Title

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.*

/**
 * E2E coverage: complete ticket lifecycle workflow.
 *
 * Tests in this spec exercise the full HTTP surface for creating, reading,
 * updating, resolving, and deleting tickets, plus auto-classification
 * behaviour, re-classification, listing filters, and validation errors.
 *
 * Each test creates its own tickets and removes them in cleanup() so tests
 * are independent and can run against a shared server without data pollution.
 */
@Title('Complete Ticket Lifecycle Workflow')
class TicketLifecycleSpec extends BaseE2ESpec {

    // -----------------------------------------------------------------------
    // Scenario 1 – Full status-machine walk
    // -----------------------------------------------------------------------

    def "complete ticket lifecycle: new → in_progress → waiting_customer → resolved → deleted → 404"() {
        when: "a technical_issue ticket is created with explicit category and priority"
        def createResp = given()
            .contentType(ContentType.JSON)
            .body('''{
                "customer_id":    "LIFECYCLE-1",
                "customer_email": "lifecycle@e2e.com",
                "customer_name":  "Lifecycle Tester",
                "subject":        "API server returning 500 errors",
                "description":    "The main API endpoint crashes on every request. Nothing is working.",
                "category":       "technical_issue",
                "priority":       "high"
            }''')
            .when().post('/tickets')
        def id = createResp.path('id') as String
        createdIds << id

        then: "201 Created with status=new and no resolved_at or classification_confidence"
        createResp.statusCode() == 201
        id != null
        createResp.path('status') == 'new'
        createResp.path('category') == 'technical_issue'
        createResp.path('priority') == 'high'
        createResp.path('created_at') != null
        createResp.path('updated_at') != null
        createResp.path('resolved_at') == null
        createResp.path('classification_confidence') == null

        when: "the ticket is fetched by ID"
        def getResp = given().get("/tickets/$id")

        then: "200 with matching ticket data"
        getResp.statusCode() == 200
        getResp.path('id') == id
        getResp.path('customer_id') == 'LIFECYCLE-1'
        getResp.path('category') == 'technical_issue'
        getResp.path('resolved_at') == null

        when: "ticket is assigned and moved to in_progress"
        def inProgressResp = given()
            .contentType(ContentType.JSON)
            .body("""{
                "customer_id":"LIFECYCLE-1","customer_email":"lifecycle@e2e.com",
                "customer_name":"Lifecycle Tester",
                "subject":"API server returning 500 errors",
                "description":"Investigating root cause of the crash.",
                "category":"technical_issue","priority":"high",
                "status":"in_progress","assigned_to":"agent-007"
            }""")
            .when().put("/tickets/$id")

        then: "200 with status=in_progress, assignee set, resolved_at still null"
        inProgressResp.statusCode() == 200
        inProgressResp.path('status') == 'in_progress'
        inProgressResp.path('assigned_to') == 'agent-007'
        inProgressResp.path('resolved_at') == null

        when: "agent requests more info – status moves to waiting_customer"
        def waitingResp = given()
            .contentType(ContentType.JSON)
            .body("""{
                "customer_id":"LIFECYCLE-1","customer_email":"lifecycle@e2e.com",
                "customer_name":"Lifecycle Tester",
                "subject":"API server returning 500 errors",
                "description":"Awaiting customer logs to reproduce the issue.",
                "category":"technical_issue","priority":"high",
                "status":"waiting_customer","assigned_to":"agent-007"
            }""")
            .when().put("/tickets/$id")

        then: "200 with status=waiting_customer"
        waitingResp.statusCode() == 200
        waitingResp.path('status') == 'waiting_customer'

        when: "fix is deployed and ticket is resolved"
        def resolveResp = given()
            .contentType(ContentType.JSON)
            .body("""{
                "customer_id":"LIFECYCLE-1","customer_email":"lifecycle@e2e.com",
                "customer_name":"Lifecycle Tester",
                "subject":"API server returning 500 errors",
                "description":"Root cause found and fixed with hotfix deployment.",
                "category":"technical_issue","priority":"high",
                "status":"resolved","assigned_to":"agent-007"
            }""")
            .when().put("/tickets/$id")

        then: "200 with status=resolved and resolved_at timestamp is populated"
        resolveResp.statusCode() == 200
        resolveResp.path('status') == 'resolved'
        resolveResp.path('resolved_at') != null

        when: "ticket is deleted"
        def deleteCode = given().delete("/tickets/$id").statusCode()
        createdIds.remove(id)

        then: "204 No Content"
        deleteCode == 204

        when: "the deleted ticket is requested"
        def notFoundResp = given().get("/tickets/$id")

        then: "404 Not Found with the standard error envelope"
        notFoundResp.statusCode() == 404
        notFoundResp.path('status') == 404
        notFoundResp.path('error') == 'Not Found'
    }

    // -----------------------------------------------------------------------
    // Scenario 2 – Auto-classify on create, then manual override clears confidence
    // -----------------------------------------------------------------------

    def "auto-classify on create assigns category and priority; manual PUT override clears classification_confidence"() {
        when: "ticket is created with auto_classify=true using account-access + urgency keywords"
        def createResp = given()
            .contentType(ContentType.JSON)
            .queryParam('auto_classify', true)
            .body('''{
                "customer_id":    "LC-AC-1",
                "customer_email": "actest@e2e.com",
                "customer_name":  "AutoClassify Tester",
                "subject":        "Cannot login",
                "description":    "I cannot access my account. Password reset failed repeatedly. This is critical."
            }''')
            .when().post('/tickets')
        def id = createResp.path('id') as String
        createdIds << id

        then: "201 with classifier-assigned account_access/urgent and positive confidence"
        createResp.statusCode() == 201
        createResp.path('category') == 'account_access'
        createResp.path('priority') == 'urgent'
        (createResp.path('classification_confidence') as Float) > 0.0f

        when: "ticket category is manually overridden to a different category via PUT"
        given()
            .contentType(ContentType.JSON)
            .body("""{
                "customer_id":"LC-AC-1","customer_email":"actest@e2e.com",
                "customer_name":"AutoClassify Tester",
                "subject":"Cannot login",
                "description":"Reclassified manually after investigation.",
                "category":"billing_question","priority":"low"
            }""")
            .when().put("/tickets/$id")
            .then().statusCode(200)

        then: "PUT succeeded without exception"
        noExceptionThrown()

        when: "ticket is fetched after the manual override"
        def fetchResp = given().get("/tickets/$id")

        then: "classification_confidence is null – the manual category change clears it"
        fetchResp.statusCode() == 200
        fetchResp.path('category') == 'billing_question'
        fetchResp.path('classification_confidence') == null
    }

    // -----------------------------------------------------------------------
    // Scenario 3 – Re-classify endpoint
    // -----------------------------------------------------------------------

    def "POST /{id}/auto-classify reclassifies existing ticket and updates category, priority, confidence"() {
        given: "a ticket created with deliberately wrong manual category and priority"
        def id = given()
            .contentType(ContentType.JSON)
            .body('''{
                "customer_id":    "LC-RC-1",
                "customer_email": "rc@e2e.com",
                "customer_name":  "Reclassify Tester",
                "subject":        "Production server crashed",
                "description":    "The production environment has crashed with critical errors. Service not working for all users. Security incident in progress.",
                "category":       "other",
                "priority":       "low"
            }''')
            .when().post('/tickets')
            .then().statusCode(201)
            .extract().path('id') as String
        createdIds << id

        when: "the auto-classify endpoint is called on the existing ticket"
        def classifyResp = given().post("/tickets/$id/auto-classify")

        then: "200 with urgent priority, technical_issue category, and matched keywords"
        classifyResp.statusCode() == 200
        classifyResp.path('category') == 'technical_issue'
        classifyResp.path('priority') == 'urgent'
        (classifyResp.path('keywords_found') as List).containsAll(['security', 'critical'])
        (classifyResp.path('confidence') as Float) > 0.0f
        (classifyResp.path('reasoning') as String) != null

        when: "ticket is fetched after re-classification"
        def fetchResp = given().get("/tickets/$id")

        then: "ticket reflects reclassified technical_issue/urgent with non-null confidence"
        fetchResp.statusCode() == 200
        fetchResp.path('category') == 'technical_issue'
        fetchResp.path('priority') == 'urgent'
        fetchResp.path('classification_confidence') != null
    }

    // -----------------------------------------------------------------------
    // Scenario 4 – Listing filters
    // -----------------------------------------------------------------------

    def "filtering tickets by category, priority, status, and combined criteria returns correct subsets"() {
        given: "three tickets with distinct category/priority combinations are created"
        createdIds << given()
            .contentType(ContentType.JSON)
            .body('''{
                "customer_id":"FILTER-C1","customer_email":"f1@e2e.com","customer_name":"Filter One",
                "subject":"Cannot login","description":"Password reset not working. Cannot access my account. Critical issue.",
                "category":"account_access","priority":"urgent"
            }''')
            .when().post('/tickets').then().statusCode(201).extract().path('id') as String

        createdIds << given()
            .contentType(ContentType.JSON)
            .body('''{
                "customer_id":"FILTER-C2","customer_email":"f2@e2e.com","customer_name":"Filter Two",
                "subject":"Invoice dispute","description":"My invoice was charged twice. Need a refund. This is important.",
                "category":"billing_question","priority":"high"
            }''')
            .when().post('/tickets').then().statusCode(201).extract().path('id') as String

        createdIds << given()
            .contentType(ContentType.JSON)
            .body('''{
                "customer_id":"FILTER-C3","customer_email":"f3@e2e.com","customer_name":"Filter Three",
                "subject":"Dark mode request","description":"Would love a dark mode feature. Minor cosmetic suggestion.",
                "category":"feature_request","priority":"low"
            }''')
            .when().post('/tickets').then().statusCode(201).extract().path('id') as String

        expect: "customer_id filter isolates the urgent account_access ticket"
        given().queryParam('customer_id', 'FILTER-C1').get('/tickets')
            .then().statusCode(200)
            .body('size()', equalTo(1))
            .body('[0].category', equalTo('account_access'))
            .body('[0].priority', equalTo('urgent'))

        and: "customer_id filter isolates the high billing_question ticket"
        given().queryParam('customer_id', 'FILTER-C2').get('/tickets')
            .then().statusCode(200)
            .body('[0].category', equalTo('billing_question'))
            .body('[0].priority', equalTo('high'))

        and: "customer_id filter isolates the low feature_request ticket"
        given().queryParam('customer_id', 'FILTER-C3').get('/tickets')
            .then().statusCode(200)
            .body('[0].category', equalTo('feature_request'))
            .body('[0].priority', equalTo('low'))

        and: "combined category + priority + customer_id filter returns exactly one ticket"
        given()
            .queryParam('category', 'account_access')
            .queryParam('priority', 'urgent')
            .queryParam('customer_id', 'FILTER-C1')
            .get('/tickets')
            .then().statusCode(200)
            .body('size()', equalTo(1))
            .body('[0].customer_id', equalTo('FILTER-C1'))

        and: "status=new with customer_id returns the ticket (default status)"
        given().queryParam('status', 'new').queryParam('customer_id', 'FILTER-C1').get('/tickets')
            .then().statusCode(200)
            .body('size()', equalTo(1))

        and: "mismatched priority filter returns no results for that customer"
        given().queryParam('customer_id', 'FILTER-C1').queryParam('priority', 'low').get('/tickets')
            .then().statusCode(200)
            .body('size()', equalTo(0))

        and: "invalid enum value for priority returns 400 Bad Request"
        given().queryParam('priority', 'NOT_VALID').get('/tickets')
            .then().statusCode(400)
    }

    // -----------------------------------------------------------------------
    // Scenario 5 – Validation errors and not-found responses
    // -----------------------------------------------------------------------

    def "validation errors return 400 with proper error envelopes; non-existent resources return 404"() {
        expect: "invalid email returns 400 with field_errors array"
        given()
            .contentType(ContentType.JSON)
            .body('''{
                "customer_id":"VAL-1","customer_email":"not-a-valid-email","customer_name":"Validator",
                "subject":"Test subject","description":"Test description long enough for validation.",
                "category":"other","priority":"low"
            }''')
            .when().post('/tickets')
            .then().statusCode(400)
            .body('field_errors', notNullValue())
            .body('error', equalTo('Validation Failed'))

        and: "description shorter than 10 chars returns 400"
        given()
            .contentType(ContentType.JSON)
            .body('''{
                "customer_id":"VAL-2","customer_email":"val2@e2e.com","customer_name":"Validator",
                "subject":"Test subject","description":"short",
                "category":"other","priority":"low"
            }''')
            .when().post('/tickets')
            .then().statusCode(400)

        and: "missing category without auto_classify returns 400 with category in message"
        given()
            .contentType(ContentType.JSON)
            .body('''{
                "customer_id":"VAL-3","customer_email":"val3@e2e.com","customer_name":"Validator",
                "subject":"Test subject","description":"Test description long enough for validation."
            }''')
            .when().post('/tickets')
            .then().statusCode(400)
            .body('message', containsString('category'))

        and: "GET for a non-existent UUID returns 404 Not Found"
        given().get('/tickets/00000000-0000-0000-0000-000000000000')
            .then().statusCode(404)
            .body('error', equalTo('Not Found'))
            .body('status', equalTo(404))

        and: "PUT for a non-existent UUID returns 404"
        given()
            .contentType(ContentType.JSON)
            .body('''{
                "customer_id":"VAL-4","customer_email":"val4@e2e.com","customer_name":"Validator",
                "subject":"Test subject","description":"Test description long enough for validation.",
                "category":"other","priority":"low"
            }''')
            .when().put('/tickets/00000000-0000-0000-0000-000000000000')
            .then().statusCode(404)

        and: "DELETE for a non-existent UUID returns 404"
        given().delete('/tickets/00000000-0000-0000-0000-000000000000')
            .then().statusCode(404)

        and: "re-classify for a non-existent UUID returns 404"
        given().post('/tickets/00000000-0000-0000-0000-000000000000/auto-classify')
            .then().statusCode(404)
    }
}
