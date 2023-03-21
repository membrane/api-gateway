/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.tests.template.json;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class JsonTemplateTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "template/json";
    }

    @Test
    void queryParamInputText() {
        given()
            .get("http://localhost:2000/json-out?answer=42")
        .then().assertThat()
            .body("answer",equalTo(42));
    }

    @Test
    void variables() {
        given()
            .contentType(APPLICATION_JSON)
            .body("""
                    {
                      "city": "Bonn",
                      "country": "Germany"
                    }""")
            .post("http://localhost:2000/json-in")
        .then().assertThat()
            .body(containsString("City: Bonn"));
    }
}