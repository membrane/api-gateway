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

package com.predic8.membrane.examples.tutorials.rest;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.FileUtil.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * See: <a href="https://membrane-api.io/tutorials/rest/">REST Tutorial</a>
 * <p>
 * Needs an Internet connection to work!
 */
public class TutorialRestStepsTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "../tutorials/rest";
    }

    private Process2 process;

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        // In the distribution is only the start of the tutorial but not alle the steps
        writeInputStreamToFile(baseDir + "/proxies.xml", getStepsProxiesAsStream());
        process = startServiceProxyScript();
    }

    @AfterEach
    void stopMembrane() throws IOException, InterruptedException {
        process.killScript();
    }

    @Test
    public void start() throws Exception {
        get(LOCALHOST_2000)
                .then()
                .assertThat()
                    .contentType(APPLICATION_JSON)
                    .body(containsString("Shop API"));

        get("http://localhost:9000/admin/")
                .then()
                .assertThat()
                    .contentType(TEXT_HTML)
                    .body(containsString("Administration"));

    }

    @Test
    public void step1() {
        get("http://localhost:2001/shop/products/")
                .then()
                .assertThat()
                    .contentType(APPLICATION_JSON)
                    .body("meta.count",greaterThan(10));

        get("http://localhost:2001")
                .then()
                .assertThat()
                    .statusCode(400);
    }

    @Test
    public void step2() {
        get("http://localhost:2001/shop/products/")
                .then()
                .assertThat()
                    .contentType(APPLICATION_JSON)
                    .body("meta.count",greaterThan(10));

        get("http://localhost:2002/restnames/name.groovy?name=Pia")
                .then()
                .assertThat()
                .contentType(APPLICATION_XML)
                .statusCode(200)
                .body("restnames.nameinfo.name", equalTo("Pia"));
    }

    @Test
    public void step3() {
        get("http://localhost:2003/restnames/name.groovy?name=Pia")
                .then()
                .assertThat()
                    .contentType(APPLICATION_JSON)
                    .statusCode(200)
                    .body("restnames.nameinfo.name", equalTo("Pia"));
    }

    /**
     * Same as Step 3 but with beautifier and ratelimiter
     */
    @Test
    public void step4() {
            get("http://localhost:2004/restnames/name.groovy?name=Pia")
                    .then()
                    .assertThat()
                    .statusCode(200)
                    .contentType(APPLICATION_JSON)
                    .body("restnames.nameinfo.name", equalTo("Pia"));

            get("http://localhost:2004/restnames/name.groovy?name=Pia")
                    .then()
                    .assertThat()
                    .statusCode(200);

            get("http://localhost:2004/restnames/name.groovy?name=Pia")
                    .then()
                    .assertThat()
                    .statusCode(200);

            get("http://localhost:2004/restnames/name.groovy?name=Pia")
                    .then()
                    .assertThat()
                    .statusCode(429);
    }

    private InputStream getStepsProxiesAsStream() {
        return getClass().getClassLoader().getResourceAsStream("com/predic8/membrane/examples/tutorials/rest/rest-tutorial-steps-proxies.xml");
    }
}
