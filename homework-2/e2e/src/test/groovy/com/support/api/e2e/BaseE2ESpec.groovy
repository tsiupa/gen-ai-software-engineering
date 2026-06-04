package com.support.api.e2e

import io.restassured.RestAssured
import io.restassured.response.Response
import spock.lang.Specification

import static io.restassured.RestAssured.given

/**
 * Abstract base for all E2E specs. Configures REST Assured base URI from the
 * system property {@code api.base-url} (default: http://localhost:8080) and
 * tracks ticket UUIDs created during each test so they can be cleaned up
 * afterward without a repository.deleteAll() dependency.
 *
 * Run against a locally-running app:
 *   cd homework-2 && mvn -DskipTests spring-boot:run
 *   cd ../e2e    && mvn test
 *
 * Run against a remote host:
 *   mvn test -Dapi.base-url=http://staging.example.com
 */
abstract class BaseE2ESpec extends Specification {

    static final String BASE_URL = System.getProperty('api.base-url', 'http://localhost:8080')

    /** UUIDs of tickets created in the current test – deleted in cleanup(). */
    protected List<String> createdIds = []

    def setup() {
        RestAssured.baseURI = BASE_URL
    }

    def cleanup() {
        createdIds.each { id ->
            try {
                given().delete("/tickets/$id")
            } catch (Exception ignored) {}
        }
        createdIds.clear()
    }

    /**
     * Parses {@code created_ids} from a bulk-import response and registers
     * them for cleanup. Call this in a {@code when:} block immediately after
     * the import request so that tickets are deleted even if subsequent
     * assertions fail.
     */
    protected void trackImportedIds(Response importResponse) {
        List<String> ids = importResponse.jsonPath().getList('created_ids')
        if (ids) {
            createdIds.addAll(ids.collect { it as String })
        }
    }
}
