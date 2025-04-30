/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class CallAuthenticationExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "orchestration/call-authentication";
    }

    @Test
    void testCall() {
        given().when().get("http://localhost:2000").then().body(containsString("Secured backend!")).statusCode(200);
    }

    @Test
    void testAuthService() {
        given().when().get("http://localhost:3000/login").then().header("X-Api-Key", "ABCDE").statusCode(200);
    }

    @Test
    void testSecuredBackend() {
        given().when().get("http://localhost:3001").then().statusCode(401);
    }
}
