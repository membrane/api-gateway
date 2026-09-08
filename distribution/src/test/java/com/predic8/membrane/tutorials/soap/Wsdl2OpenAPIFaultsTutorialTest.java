/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.tutorials.soap;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Wsdl2OpenAPIFaultsTutorialTest extends AbstractSOAPTutorialTest {

    private static final String CANCEL = "http://localhost:2000/orders/cancel";
    private static final String SPEC_PATH = "/api-docs/order-api-v1-0-0";

    @Override
    protected String getTutorialYaml() {
        return "98-WSDL-Faults.yaml";
    }

    /** Step 2: the fault whose content model carries the state information. */
    @Test
    void wrongStateFaultAppearsUnderDetails() {
        cancel("1")
            .body("title", equalTo("Operation failed"))
            .body("faultMessage", equalTo("Cancellation is not possible in this state"))
            .body("details.wrongState.orderId", equalTo("1"))
            .body("details.wrongState.currentState", equalTo("SHIPPED"))
            .body("details.wrongState.message", equalTo("A shipped order cannot be cancelled."));
    }

    /** Step 3: the other declared fault — a different key and a different content model. */
    @Test
    void orderDoesNotExistFaultAppearsUnderDetails() {
        cancel("4711")
            .body("title", equalTo("Operation failed"))
            .body("faultMessage", equalTo("Order does not exist"))
            .body("details.orderDoesNotExist.orderId", equalTo("4711"))
            .body("details.orderDoesNotExist.currentState", nullValue())
            .body("details.wrongState", nullValue());
    }

    /**
     * Step 4: the mock answers both faults with HTTP 200. The status the client sees comes from
     * the fault in the SOAP body, not from the backend's status code.
     */
    @Test
    void faultBecomesProblemDetailsWith500() {
        cancel("1")
            .body("status", equalTo(500))
            .body("type", equalTo("https://membrane-api.io/problems/operation-error"));
    }

    /** Step 5: no part of the contract names the technology behind the API. */
    @Test
    void responseDoesNotNameTheSoapBackend() {
        String body = cancel("1").extract().asString();

        assertFalse(body.toLowerCase().contains("soap"),
                "the response must not reveal that a SOAP service is called: " + body);
    }

    /** Step 6: one default response covering every error, with both faults combined by oneOf. */
    @Test
    void specDescribesTheFaultsUnderOneDefaultResponse() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000" + SPEC_PATH)
        .then()
            .statusCode(200)
            .body(containsString("default:"))
            // the document is YAML, so a 500 response key could be spelled 500:, "500": or '500':
            .body(not(matchesPattern("(?s).*[\"']?500[\"']?\\s*:.*")))
            .body(containsString("application/problem+json"))
            .body(containsString("#/components/schemas/ProblemDetails"))
            .body(containsString("oneOf"))
            .body(containsString("orderDoesNotExist"))
            .body(containsString("wrongState"));
        // @formatter:on
    }

    private static ValidatableResponse cancel(String orderId) {
        // @formatter:off
        return given()
            .contentType(JSON)
            .body("{\"orderId\":\"%s\"}".formatted(orderId))
        .when()
            .post(CANCEL)
        .then()
            .statusCode(500)
            .contentType("application/problem+json");
        // @formatter:on
    }
}
