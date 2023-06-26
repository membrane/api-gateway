package com.predic8.membrane.examples.tests;

import com.predic8.membrane.core.util.Pair;
import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.Process2;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.test.AssertUtils.disableHTTPAuthentication;
import static com.predic8.membrane.test.AssertUtils.postAndAssert;
import static io.restassured.RestAssured.given;
import static java.lang.Thread.sleep;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JsonProtectionTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "json-protection";
    }

    private final HashMap<String, Integer> statusCodeFileMap = new HashMap<>() {{
        put("valid.json", 200);
        put("max_tokens.json", 400);
        put("max_size.json", 400);
        put("max_depth.json", 400);
        put("max_string_length.json", 400);
        put("max_key_length.json", 400);
        put("max_object_size.json", 400);
        put("max_array_size.json", 400);
    }};

    final List<String> parameters() {
        return statusCodeFileMap.keySet().stream().toList();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testEndpoint(String filename) throws Exception {
        given()
            .contentType(ContentType.JSON)
            .body(readFileFromBaseDir("requests/" + filename).replaceAll("\r|\n", ""))
        .when()
            .post("http://localhost:2000/")
        .then()
            .statusCode(statusCodeFileMap.get(filename));
    }
}
