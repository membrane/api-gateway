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
import org.skyscreamer.jsonassert.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class ProxiesXMLOfflineTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "..";
    }
    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        process =  new Process2.Builder().in(baseDir).script("service-proxy").parameters("-c conf/proxies-offline.xml").waitForMembrane().start();

        // Dump HTTP
        RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    @SuppressWarnings("JsonSchemaCompliance")
    @Test
    void api_doc() {
        JSONAssert.assertEquals("""
                {
                  "fruitshop-v1-0" : {
                    "openapi" : "3.0.2",
                    "title" : "Fruit Shop API",
                    "version" : "1.0",
                    "openapi_link" : "/api-doc/fruitshop-v1-0",
                    "ui_link" : "/api-doc/ui/fruitshop-v1-0"
                  }
                }
                """, get(LOCALHOST_2000 + "/api-doc").asString(), true);
    }

    @Test
    public void port2000() {
        get(LOCALHOST_2000)
                .then().assertThat()
                .contentType(APPLICATION_JSON)
                .body("success", equalTo(true));
    }
}