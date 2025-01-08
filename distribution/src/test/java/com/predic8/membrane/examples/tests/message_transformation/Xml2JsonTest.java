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
package com.predic8.membrane.examples.tests.message_transformation;

import com.predic8.membrane.examples.util.*;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.test.AssertUtils.*;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.XML;
import static java.lang.Thread.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class Xml2JsonTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "message-transformation/xml2json";
    }

    @Test
    public void test() throws Exception {
        BufferLogger logger = new BufferLogger();
        try(Process2 ignored = startServiceProxyScript(logger)) {
            // @formatter:off
            given()
                .contentType(XML)
                .body(readFileFromBaseDir("jobs.xml"))
            .when()
                .post(LOCALHOST_2000)
.then()
                    .statusCode(200);
            // @formatter:on
            assertThat(logger.toString(), containsString("{\"jobs\":{\"job\":"));
        }
    }
}