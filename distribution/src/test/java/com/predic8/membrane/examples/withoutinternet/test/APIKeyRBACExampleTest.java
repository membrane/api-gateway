/* Copyright 2024 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.CoreMatchers.*;

public class APIKeyRBACExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "security/api-key/rbac";
    }

    @Test
    public void normalScope() {
        given()
            .header("X-Key", "123456789")
        .when()
            .get("http://localhost:3000")
        .then().assertThat()
            .statusCode(200)
            .body(containsString("Caller scopes"))
            .body(containsString("accounting"))
            .body(containsString("finance"));
    }

    @Test
    public void conditionalScope() {
        given()
            .header("X-Key", "key_321_abc")
        .when()
            .get("http://localhost:3000")
        .then().assertThat()
            .statusCode(200)
            .body(containsString("Caller scopes"))
            .body(containsString("admin"));
    }
}
