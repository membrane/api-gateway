package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
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
public class JsonProtectionTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "json-protection";
    }

    @Test
    public void testValid() throws Exception {
        postAndAssert(
                200,
                "http://localhost:2000/",
                new String[]{"Content-Type", APPLICATION_JSON},
                readFileFromBaseDir("requests/valid.json")
        );
    }
    @Test
    public void testMaxTokens() throws Exception {
        postAndAssert(
                400,
                "http://localhost:2000/",
                new String[]{"Content-Type", APPLICATION_JSON},
                readFileFromBaseDir("requests/max_tokens.json")
        );
    }
    @Test
    public void testMaxSize() throws Exception {
        postAndAssert(
                400,
                "http://localhost:2000/",
                new String[]{"Content-Type", APPLICATION_JSON},
                readFileFromBaseDir("requests/max_size.json")
        );
    }
    @Test
    public void testMaxDepth() throws Exception {
        postAndAssert(
                400,
                "http://localhost:2000/",
                new String[]{"Content-Type", APPLICATION_JSON},
                readFileFromBaseDir("requests/max_depth.json")
        );
    }
    @Test
    public void testStringLength() throws Exception {
        postAndAssert(
                400,
                "http://localhost:2000/",
                new String[]{"Content-Type", APPLICATION_JSON},
                readFileFromBaseDir("requests/max_string_length.json")
        );
    }
    @Test
    public void testMaxKeyLength() throws Exception {
        postAndAssert(
                400,
                "http://localhost:2000/",
                new String[]{"Content-Type", APPLICATION_JSON},
                readFileFromBaseDir("requests/max_key_length.json")
        );
    }
    @Test
    public void testMaxObjectSize() throws Exception {
        postAndAssert(
                400,
                "http://localhost:2000/",
                new String[]{"Content-Type", APPLICATION_JSON},
                readFileFromBaseDir("requests/max_object_size.json")
        );
    }
    @Test
    public void testMaxArraySize() throws Exception {
        postAndAssert(
                400,
                "http://localhost:2000/",
                new String[]{"Content-Type", APPLICATION_JSON},
                readFileFromBaseDir("requests/max_array_size.json")
        );
    }
}
