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

package com.predic8.membrane.tutorials.openapi;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class OpenApiXmlTutorialTest extends AbstractOpenAPITutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "70-XML.apis.yaml";
    }

    @Test
    void validCustomer() {
        postCustomer("<customer id='7'><name>Alice</name><age>42</age></customer>")
            .statusCode(201);
    }

    @Test
    void negativeAge() {
        postCustomer("<customer id='7'><name>Alice</name><age>-1</age></customer>")
            .statusCode(400)
            .body(containsString("<entry key=\"REQUEST/BODY#/age\">"))
            .body(containsString("-1 is smaller than the minimum of 0"));
    }

    @Test
    void idAttributeNotAnInteger() {
        postCustomer("<customer id='seven'><name>Alice</name><age>42</age></customer>")
            .statusCode(400)
            .body(containsString("<entry key=\"REQUEST/BODY#/id\">"))
            .body(containsString("\"seven\" is of type string which does not match any of [integer]"));
    }

    private static ValidatableResponse postCustomer(String xml) {
        // @formatter:off
        return given()
            .contentType("application/xml")
            .body(xml)
        .when()
            .post("http://localhost:2000/crm/customers")
        .then();
        // @formatter:on
    }
}
