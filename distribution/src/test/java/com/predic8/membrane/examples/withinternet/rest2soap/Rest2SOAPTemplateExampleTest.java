/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.examples.withinternet.rest2soap;

import com.predic8.membrane.examples.util.*;
import org.hamcrest.*;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static io.restassured.filter.log.LogDetail.*;

public class Rest2SOAPTemplateExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "web-services-soap/rest2soap-template";
    }

    @Test
    public void test() throws Exception {

        try (Process2 ignored = startServiceProxyScript()) {
            // @formatter:off
            given()
                .get("http://localhost:2000/cities/Bielefeld")
            .then()
                .log().ifValidationFails(ALL)
                .statusCode(200)
                .body("population", Matchers.equalTo("333000"));
            // @formatter:on
        }
    }

}
