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

package com.predic8.membrane.examples.withoutinternet.test;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

public class ConfigurationIncludesExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "configuration/includes";
    }

    @Test
    void includedApisAreLoaded() {
        // @formatter:off
        when().get("http://localhost:2000/customers")
        .then()
            .statusCode(200)
            .body("count", equalTo(2))
            .body("customers.id", hasItems("c-1001", "c-1002"))
            .body("customers.name", hasItems("Alice Johnson", "Bob Miller"));

        when().get("http://localhost:2000/orders")
        .then()
            .statusCode(200)
            .body("count", equalTo(2))
            .body("orders.id", hasItems("o-9001", "o-9002"))
            .body("orders.status", hasItems("processing", "shipped"));
        // @formatter:on
    }

    @Test
    void fallbackReturnsNotFoundMessage() {
        // @formatter:off
        when().get("http://localhost:2000/does-not-exist")
        .then()
            .statusCode(404)
            .body("message", equalTo("Not Found!"));
        // @formatter:on
    }
}
