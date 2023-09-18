package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;

import static io.restassured.RestAssured.given;

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
        return "security/json-protection";
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
