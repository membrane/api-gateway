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

package com.predic8.membrane.tutorials.openapi;

import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenApiValidationAdvancedTutorialTest extends AbstractOpenAPITutorialTest {

    // Starts at the line break, so the replacement does not touch the instructions quoting the line
    private static final String VALIDATOR_COMMENTED = "\n#    - openapiValidator: {}";
    private static final String VALIDATOR_ACTIVE = "\n    - openapiValidator: {}";
    private static final String VALIDATION_FAILED = "OpenAPI request validation failed";
    private static final String FLOW_LOG = "==== REQUEST";

    @Override
    protected String getTutorialYaml() {
        return "21-Validation-Advanced.apis.yaml";
    }

    @Test
    void validationBeforeFlowSkipsLog() throws Exception {
        postNegativePrice();
        assertFalse(logger.contains(FLOW_LOG));
    }

    @Test
    void validationInFlowRunsAfterLog() throws Exception {
        restartWith(VALIDATOR_COMMENTED, VALIDATOR_ACTIVE);
        try {
            postNegativePrice();
            assertTrue(logger.contains(FLOW_LOG));
        } finally {
            restartWith(VALIDATOR_ACTIVE, VALIDATOR_COMMENTED);
        }
    }

    private void restartWith(String from, String to) throws Exception {
        stopMembrane();
        replaceInFile2(getTutorialYaml(), from, to);
        startMembrane();
    }

    /**
     * Returns once the validation failure is logged, so every line of the flow log before it is in the console.
     */
    private void postNegativePrice() throws Exception {
        final var validationFailed = new SubstringWaitableConsoleEvent(process, VALIDATION_FAILED);

        // @formatter:off
        given()
            .contentType("application/json")
            .body("""
            { "name": "Figs", "price": -2.7 }
            """)
        .when()
            .post("http://localhost:2000/store/products")
        .then()
            .statusCode(400)
            .body("title", equalTo("OpenAPI message validation failed"));
        // @formatter:on

        validationFailed.waitFor(5000);
    }
}
