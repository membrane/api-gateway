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
package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class StaxExampleInterceptorTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "stax-interceptor";
    }

    @Test
    public void test() throws Exception {

        compileCustomInterceptor();

        try(Process2 ignored = startServiceProxyScript()) {

            // @formatter:off
            given()
                .contentType(TEXT_XML)
                .body(readFileFromBaseDir("request.xml"))
                .post(LOCALHOST_2000)
            .then()
                .body("bar.bar", equalTo("42"));
            // @formatter:on
        }
    }

    private void compileCustomInterceptor() throws Exception {
        BufferLogger logger = new BufferLogger();
        try(Process2 mvn = new Process2.Builder().in(baseDir).executable("mvn package").withWatcher(logger).start())  {
            if (mvn.waitForExit(60000) != 0)
                throw new RuntimeException("Maven exited with code " + mvn.waitForExit(60000) + ": " + logger);
        }
    }
}
