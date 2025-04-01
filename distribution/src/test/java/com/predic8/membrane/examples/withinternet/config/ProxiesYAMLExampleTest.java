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
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.TEXT_HTML;
import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxiesYAMLExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "yaml-configuration";
    }

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        process =  new Process2.Builder().in(baseDir).script("membrane").parameters("yaml -l proxies.yaml").waitForMembrane().start();

        // Dump HTTP
        //RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }


    @Test
    void api_doc() throws JSONException {
        JSONAssert.assertEquals("""
                {
                  "fruitshop-v1-1" : {
                    "openapi" : "3.0.2",
                    "title" : "Fruit Shop API",
                    "version" : "1.1",
                    "openapi_link" : "/api-docs/fruitshop-v1-1",
                    "ui_link" : "/api-docs/ui/fruitshop-v1-1"
                  }
                }
                """, get(LOCALHOST_2000 + "/api-docs").asString(), true);
    }

    @Test
    void adminConsole() {
        // @formatter:off
        given().
        when().
                get("http://localhost:9000/admin").
        then().assertThat()
                .statusCode(200)
                .contentType(TEXT_HTML)
                .body(containsString("Administration"));
        // @formatter:on
    }
}