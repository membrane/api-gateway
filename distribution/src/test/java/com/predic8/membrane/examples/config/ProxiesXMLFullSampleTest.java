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

package com.predic8.membrane.examples.config;

import com.predic8.membrane.examples.util.*;
import io.restassured.*;
import io.restassured.filter.log.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;

public class ProxiesXMLFullSampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "..";
    }

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        process =  new Process2.Builder().in(baseDir).script("service-proxy").parameters("-c conf/proxies-full-sample.xml").waitForMembrane().start();

        // Dump HTTP
        // RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }


    @Test
    void staticWeb() {
        // @formatter:off
        get("http://localhost:2001/static/proxies.xml")
        .then().assertThat()
                .statusCode(200)
                .contentType(TEXT_XML);
        // @formatter:on
    }

    @Test
    void adminConsoleWithoutAuth() {
        // @formatter:off
        get("http://localhost:9000/admin")
        .then().assertThat()
                .statusCode(401);
        // @formatter:on
    }

    @Test
    void adminConsoleWithAuth() {
        // @formatter:off
        given().
            auth().basic("admin","membrane").
        when().
            get("http://localhost:9000/admin").
        then().assertThat()
            .statusCode(200)
            .contentType(TEXT_HTML);
        // @formatter:on
    }
}