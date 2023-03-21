/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.tests.template.text;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class TextTemplateTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "template/text";
    }

    @Test
    void queryParamInputText() {
        given()
            .get("http://localhost:2000/text?name=Joe")
        .then().assertThat()
            .body(containsString("Hello Joe!"));
    }

    @Test
    void variables() {
        given()
            .get("http://localhost:2000/variables?a=1&b=foo")
        .then().assertThat()
            .body(containsString("Header:"))
            .body(containsString("Flow: REQUEST"))
            .body(containsString("Message.version: 1.1"))
            .body(containsString("a : 1"))
            .body(containsString("b : foo"));
    }
}