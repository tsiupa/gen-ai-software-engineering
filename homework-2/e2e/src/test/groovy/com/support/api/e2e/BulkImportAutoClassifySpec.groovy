package com.support.api.e2e

import io.restassured.http.ContentType
import spock.lang.Title

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.*

/**
 * E2E coverage: bulk import endpoint with auto-classification verification.
 *
 * Tests in this spec verify:
 *  - JSON / CSV / XML import with auto_classify=true correctly classifies each
 *    record using the keyword-based classifier (category + priority + confidence).
 *  - Partial-success path (207 Multi-Status) with per-record error detail.
 *  - Malformed files are rejected with 400 "Invalid Import File".
 *  - Missing file part returns 400 Bad Request.
 *  - Import without auto_classify fails when records omit category/priority.
 *
 * Fixture files in src/test/resources/fixtures/ contain records with strong
 * keyword signals but NO explicit category or priority, so the classifier
 * outcome is deterministic and verifiable.
 *
 * Classifier keyword reference (from TicketClassifier):
 *  - account_access : login, password, 2fa, locked out, cannot access
 *  - billing_question: invoice, refund, payment, subscription, credit card, charge
 *  - bug_report      : steps to reproduce, regression, defect
 *  - technical_issue : error, crash, not working, exception
 *  - feature_request : feature, would love, enhancement, suggestion, improvement
 *  - urgent priority : critical, cannot access, production down, security
 *  - high priority   : important, blocking, asap
 *  - low priority    : minor, cosmetic, suggestion
 */
@Title('Bulk Import with Auto-Classification Verification')
class BulkImportAutoClassifySpec extends BaseE2ESpec {

    // -----------------------------------------------------------------------
    // Scenario 1 – JSON import
    // -----------------------------------------------------------------------

    def "JSON bulk import with auto_classify verifies per-record classification and confidence"() {
        given: "a JSON fixture with 3 records containing keyword signals but no explicit category or priority"
        def fixture = getClass().getResourceAsStream('/fixtures/e2e_auto_classify.json').bytes

        when: "the file is imported with auto_classify=true"
        def importResp = given()
            .multiPart('file', 'e2e_auto_classify.json', fixture, 'application/json')
            .queryParam('auto_classify', true)
            .when().post('/tickets/import')
        trackImportedIds(importResp)

        then: "all 3 records imported successfully with 0 failures"
        importResp.statusCode() == 201
        importResp.path('total_records') == 3
        importResp.path('successful') == 3
        importResp.path('failed') == 0

        when: "each imported ticket is fetched by its known customer_id"
        def respA = given().queryParam('customer_id', 'E2E-JSON-A').get('/tickets')
        def respB = given().queryParam('customer_id', 'E2E-JSON-B').get('/tickets')
        def respC = given().queryParam('customer_id', 'E2E-JSON-C').get('/tickets')

        then: "E2E-JSON-A → account_access / urgent (locked out + 2FA + cannot access = critical)"
        respA.path('[0].category') == 'account_access'
        respA.path('[0].priority') == 'urgent'
        (respA.path('[0].classification_confidence') as Float) > 0.0f

        and: "E2E-JSON-B → billing_question / high (subscription + payment + refund + credit card = important)"
        respB.path('[0].category') == 'billing_question'
        respB.path('[0].priority') == 'high'
        (respB.path('[0].classification_confidence') as Float) > 0.0f

        and: "E2E-JSON-C → feature_request / low (would love + enhancement + minor + suggestion)"
        respC.path('[0].category') == 'feature_request'
        respC.path('[0].priority') == 'low'
        (respC.path('[0].classification_confidence') as Float) > 0.0f
    }

    // -----------------------------------------------------------------------
    // Scenario 2 – CSV import
    // -----------------------------------------------------------------------

    def "CSV bulk import with auto_classify classifies all 5 records by keyword-driven rules"() {
        given: "a CSV fixture with 5 records – no category or priority columns"
        def fixture = getClass().getResourceAsStream('/fixtures/e2e_auto_classify.csv').bytes

        when: "the file is imported with auto_classify=true"
        def importResp = given()
            .multiPart('file', 'e2e_auto_classify.csv', fixture, 'text/csv')
            .queryParam('auto_classify', true)
            .when().post('/tickets/import')
        trackImportedIds(importResp)

        then: "all 5 records imported with no failures"
        importResp.statusCode() == 201
        importResp.path('total_records') == 5
        importResp.path('successful') == 5
        importResp.path('failed') == 0

        when: "each record is fetched by its customer_id"
        def respA = given().queryParam('customer_id', 'E2E-CSV-A').get('/tickets')
        def respB = given().queryParam('customer_id', 'E2E-CSV-B').get('/tickets')
        def respC = given().queryParam('customer_id', 'E2E-CSV-C').get('/tickets')
        def respD = given().queryParam('customer_id', 'E2E-CSV-D').get('/tickets')
        def respE = given().queryParam('customer_id', 'E2E-CSV-E').get('/tickets')

        then: "E2E-CSV-A → account_access / urgent (cannot access + password + critical)"
        respA.path('[0].category') == 'account_access'
        respA.path('[0].priority') == 'urgent'

        and: "E2E-CSV-B → billing_question / high (invoice + payment + charge + refund + important)"
        respB.path('[0].category') == 'billing_question'
        respB.path('[0].priority') == 'high'

        and: "E2E-CSV-C → feature_request / low (feature + would love + suggestion + minor)"
        respC.path('[0].category') == 'feature_request'
        respC.path('[0].priority') == 'low'

        and: "E2E-CSV-D → bug_report / medium (steps to reproduce + regression win over crash; no priority kw)"
        respD.path('[0].category') == 'bug_report'
        respD.path('[0].priority') == 'medium'

        and: "E2E-CSV-E → technical_issue / urgent (error + not working + critical + security)"
        respE.path('[0].category') == 'technical_issue'
        respE.path('[0].priority') == 'urgent'
    }

    // -----------------------------------------------------------------------
    // Scenario 3 – XML import
    // -----------------------------------------------------------------------

    def "XML bulk import with auto_classify classifies all 3 records correctly"() {
        given: "an XML fixture with 3 records containing classification keywords"
        def fixture = getClass().getResourceAsStream('/fixtures/e2e_auto_classify.xml').bytes

        when: "the file is imported with auto_classify=true"
        def importResp = given()
            .multiPart('file', 'e2e_auto_classify.xml', fixture, 'application/xml')
            .queryParam('auto_classify', true)
            .when().post('/tickets/import')
        trackImportedIds(importResp)

        then: "all 3 records imported with no failures"
        importResp.statusCode() == 201
        importResp.path('total_records') == 3
        importResp.path('successful') == 3
        importResp.path('failed') == 0

        when: "each record is fetched by its customer_id"
        def respA = given().queryParam('customer_id', 'E2E-XML-A').get('/tickets')
        def respB = given().queryParam('customer_id', 'E2E-XML-B').get('/tickets')
        def respC = given().queryParam('customer_id', 'E2E-XML-C').get('/tickets')

        then: "E2E-XML-A → account_access / urgent (login + password + locked out + critical)"
        respA.path('[0].category') == 'account_access'
        respA.path('[0].priority') == 'urgent'

        and: "E2E-XML-B → billing_question / high (invoice + charge + refund + important)"
        respB.path('[0].category') == 'billing_question'
        respB.path('[0].priority') == 'high'

        and: "E2E-XML-C → feature_request / low (would love + feature + enhancement + suggestion + minor)"
        respC.path('[0].category') == 'feature_request'
        respC.path('[0].priority') == 'low'
    }

    // -----------------------------------------------------------------------
    // Scenario 4 – Partial success (207 Multi-Status)
    // -----------------------------------------------------------------------

    def "partial JSON import with one invalid record returns 207 Multi-Status with per-record error detail"() {
        given: "a JSON payload: record 0 is valid, record 1 has an invalid email"
        def payload = '''[
            {
                "customer_id":    "PARTIAL-A",
                "customer_email": "partial-a@e2e.com",
                "customer_name":  "Partial A",
                "subject":        "Cannot login",
                "description":    "I cannot access my account. Password reset is failing. This is critical."
            },
            {
                "customer_id":    "PARTIAL-B",
                "customer_email": "NOT-A-VALID-EMAIL",
                "customer_name":  "Partial B",
                "subject":        "Bug report",
                "description":    "Steps to reproduce: click checkout button. App crashes with a regression."
            }
        ]'''

        when: "payload is imported with auto_classify=true"
        def importResp = given()
            .multiPart('file', 'partial.json', payload.bytes, 'application/json')
            .queryParam('auto_classify', true)
            .when().post('/tickets/import')
        trackImportedIds(importResp)

        then: "207 Multi-Status: 1 succeeded, 1 failed with per-record error"
        importResp.statusCode() == 207
        importResp.path('total_records') == 2
        importResp.path('successful') == 1
        importResp.path('failed') == 1
        importResp.path('errors[0].record_index') == 1
        (importResp.path('errors[0].message') as String) != null

        and: "the valid record (PARTIAL-A) was persisted and auto-classified as account_access"
        given().queryParam('customer_id', 'PARTIAL-A').get('/tickets')
            .then().statusCode(200)
            .body('size()', equalTo(1))
            .body('[0].category', equalTo('account_access'))
            .body('[0].classification_confidence', notNullValue())
    }

    // -----------------------------------------------------------------------
    // Scenario 5 – Malformed files (data-driven)
    // -----------------------------------------------------------------------

    def "#format import file returns 400 Invalid Import File"() {
        when: "a malformed #format file is uploaded"
        def response = given()
            .multiPart('file', filename, content.bytes, contentType)
            .when().post('/tickets/import')

        then: "400 Bad Request with 'Invalid Import File' error"
        response.statusCode() == 400
        response.path('error') == 'Invalid Import File'

        where:
        format  | filename     | contentType        | content
        'CSV'   | 'bad.csv'   | 'text/csv'         | '"'
        'JSON'  | 'bad.json'  | 'application/json' | 'not valid json at all'
        'XML'   | 'bad.xml'   | 'application/xml'  | '<bad unclosed tag'
    }

    // -----------------------------------------------------------------------
    // Scenario 6 – Missing file part
    // -----------------------------------------------------------------------

    def "import request without the file part returns 400 Bad Request"() {
        when: "POST /tickets/import is called with a multipart body but no 'file' part"
        def response = given()
            .multiPart('other_field', 'dummy', 'text/plain')
            .when().post('/tickets/import')

        then: "400 with Bad Request error and message indicating the missing part"
        response.statusCode() == 400
        response.path('error') == 'Bad Request'
        (response.path('message') as String).toLowerCase().contains('file')
    }

    // -----------------------------------------------------------------------
    // Scenario 7 – Import without auto_classify when category is missing
    // -----------------------------------------------------------------------

    def "import without auto_classify fails for every record that omits category or priority"() {
        given: "a JSON payload with 2 records that have no category or priority"
        def payload = '''[
            {
                "customer_id":    "NO-CAT-A",
                "customer_email": "no-cat-a@e2e.com",
                "customer_name":  "No Category A",
                "subject":        "Ticket without category",
                "description":    "This ticket intentionally omits category and priority fields."
            },
            {
                "customer_id":    "NO-CAT-B",
                "customer_email": "no-cat-b@e2e.com",
                "customer_name":  "No Category B",
                "subject":        "Another ticket without category",
                "description":    "This ticket also omits category and priority for testing purposes."
            }
        ]'''

        when: "imported WITHOUT auto_classify (default false)"
        def importResp = given()
            .multiPart('file', 'no_category.json', payload.bytes, 'application/json')
            .when().post('/tickets/import')

        then: "207 Multi-Status: both records fail because category is required when auto_classify=false"
        importResp.statusCode() == 207
        importResp.path('total_records') == 2
        importResp.path('successful') == 0
        importResp.path('failed') == 2
        (importResp.path('errors[0].message') as String).contains('category')
        (importResp.path('errors[1].message') as String).contains('category')
    }
}
