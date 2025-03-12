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
package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.containsString;

// TODO move to `withoutinternet` directory when pr #1631 is merged
public class SecuredWsdlExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    public String getExampleDirName() {
        return "web-services-soap/secured-wsdl";
    }

    @Test
    void testSecuredWsdl() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2010/services?wsdl")
        .then()
            .statusCode(200)
            .contentType(XML)
            .body(containsString("wsdl:definitions"));
        // @formatter:on
    }
}
