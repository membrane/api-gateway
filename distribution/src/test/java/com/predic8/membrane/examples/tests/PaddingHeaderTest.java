package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static java.util.regex.Pattern.compile;
import static org.hamcrest.Matchers.matchesPattern;

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
public class PaddingHeaderTest extends AbstractSampleMembraneStartStopTestcase {

    static final Pattern PATTERN = compile("^[abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _:;.,\\\\/\"'?!(){}\\[\\]@<>=\\-+*#$&`|~^%]*$");

    @Override
    protected String getExampleDirName() {
        return "security/padding-header";
    }

    @RepeatedTest(10)
    public void testHeader() throws Exception {
        given()
            .when()
            .get("http://localhost:2000/")
            .then()
            .header("X-Padding", matchesPattern(PATTERN))
            .statusCode(200);
    }
}
