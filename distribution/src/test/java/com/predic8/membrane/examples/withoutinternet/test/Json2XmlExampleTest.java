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
package com.predic8.membrane.examples.withoutinternet.test;

import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Json2XmlExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "message-transformation/json2xml";
    }

    @Test
    public void test() throws Exception {
        BufferLogger logger = new BufferLogger();
        try (Process2 ignored = new Process2.Builder().in(baseDir).script("membrane").waitForMembrane().withWatcher(logger).start()) {
            // @formatter:off
            given()
                .contentType(JSON)
                .body(readFileFromBaseDir("customers.json"))
            .when()
                .post("http://localhost:2000/")
            .then()
                .statusCode(200);
            // @formatter:on
            assertTrue(logger.toString().contains("<count>269</count>"));
        }
    }
}