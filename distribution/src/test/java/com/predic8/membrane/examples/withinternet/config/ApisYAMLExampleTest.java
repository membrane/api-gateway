/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.withinternet.config;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.MimeType.TEXT_HTML;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class ProxiesYAMLExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "yaml-configuration";
    }

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        process = new Process2.Builder().in(baseDir).script("membrane").parameters("-c apis.yaml").waitForMembrane().start();
    }

    @Test
    void api_doc_with_rest_assured() {
        // @formatter:off
        given()
        .when()
            .get(LOCALHOST_2000 + "/api-docs")
        .then()
            .statusCode(200)
            .body("$", aMapWithSize(1))
            .body("$", hasKey("fruit-shop-api-v2-2-0"))
            .body("fruit-shop-api-v2-2-0.openapi", equalTo("3.0.3"))
            .body("fruit-shop-api-v2-2-0.title", equalTo("Fruit Shop API"))
            .body("fruit-shop-api-v2-2-0.version", equalTo("2.2.0"))
            .body("fruit-shop-api-v2-2-0.openapi_link", equalTo("/api-docs/fruit-shop-api-v2-2-0"))
            .body("fruit-shop-api-v2-2-0.ui_link", equalTo("/api-docs/ui/fruit-shop-api-v2-2-0"))
            .body("fruit-shop-api-v2-2-0", aMapWithSize(5));
        // @formatter:on
    }

    @Test
    void adminConsole() {
        // @formatter:off
        given().
        when().
                get("http://localhost:9000/admin").
        then().assertThat()
                .statusCode(200)
                .header(CONTENT_TYPE, startsWith(TEXT_HTML))
                .body(containsString("Administration"));
        // @formatter:on
    }
}