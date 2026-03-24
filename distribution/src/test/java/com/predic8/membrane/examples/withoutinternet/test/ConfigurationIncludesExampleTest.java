/* Copyright 2026 predic8 GmbH, www.predic8.com

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

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;

public class ConfigurationIncludesExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "configuration/includes";
    }

    @Test
    void includesFromFileAndDirectoryAreLoaded() {
        // @formatter:off
        when().get("http://localhost:2000/root")
        .then()
            .statusCode(200)
            .body("source", equalTo("root"));

        when().get("http://localhost:2000/from-file")
        .then()
            .statusCode(200)
            .body("source", equalTo("from-file"));

        when().get("http://localhost:2000/nested")
        .then()
            .statusCode(200)
            .body("source", equalTo("nested"));

        when().get("http://localhost:2000/from-directory-a")
        .then()
            .statusCode(200)
            .body("source", equalTo("from-directory-a"));

        when().get("http://localhost:2000/from-directory-b")
        .then()
            .statusCode(200)
            .body("source", equalTo("from-directory-b"));
        // @formatter:on
    }

    @Test
    void includeDirectoryIgnoresNonApisYamlFiles() {
        // @formatter:off
        when().get("http://localhost:2000/ignored")
        .then()
            .statusCode(404);
        // @formatter:on
    }
}
