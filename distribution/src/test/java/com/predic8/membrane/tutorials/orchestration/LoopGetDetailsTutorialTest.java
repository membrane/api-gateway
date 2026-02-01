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

package com.predic8.membrane.tutorials.orchestration;

import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class LoopGetDetailsTutorialTest extends AbstractOrchestrationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "50-Loop-get-Details.yaml";
    }

    @Test
    void pricesAreLoadedPerProduct() throws Exception {
        var fetchingPrice = new SubstringWaitableConsoleEvent(process, "Fetching price for:");

        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body("products", hasSize(greaterThan(0)))
            .body("products[0].name", not(isEmptyOrNullString()))
            .body("products[0].price", notNullValue());
        // @formatter:on

        waitForOrFail(fetchingPrice, 8000);
    }

    private void waitForOrFail(SubstringWaitableConsoleEvent event, long timeoutMs) throws TimeoutException {
        event.waitFor(timeoutMs);
    }
}
