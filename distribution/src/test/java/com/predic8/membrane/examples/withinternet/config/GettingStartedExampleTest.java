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

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * Tests the guide at:
 * <a href="https://membrane-api.io/getting-started">...</a>
 * <p>
 * Needs an Internet connection to work!
 */
public class GettingStartedExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "..";
    }

    @Test
    public void test() throws Exception {
        get(LOCALHOST_2000 + "/shop/v2/products/")
        .then().assertThat()
                .statusCode(200)
                .contentType(APPLICATION_JSON)
                .body("meta.count", greaterThanOrEqualTo(0));
    }
}
