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
package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SampleSoapServiceTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "soap/sample-soap-service";
    }

    private final HashMap<String, String> methodGETmap = new HashMap<>() {{
        String soapWsdlSubStr = "<wsdl:definitions xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/";
        put("/foo?wsdl", soapWsdlSubStr);
        put("/foo/bar?wSdL", soapWsdlSubStr);
        put("/foo", "<faultstring>Method Not Allowed</faultstring>");

    }};

    final List<String> parameters() {
        return methodGETmap.keySet().stream().toList();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testMethod(String query) {
        given()
        .when()
            .get("http://localhost:2000/" + query)
        .then()
            .body(containsString(methodGETmap.get(query)));

    }


    private final HashMap<String, String> cityMap = new HashMap<>() {{
        put("Bonn", "<population>327000</population>");
        put("London", "<population>8980000</population>");
        put("Berlin", "<errorcode>404</errorcode>");

    }};

    final List<String> cityMap() {
        return cityMap.keySet().stream().toList();
    }

    @ParameterizedTest
    @MethodSource("cityMap")
    public void testCity(String city) throws Exception {
        given()
            .body(readFileFromBaseDir("request.xml").replace("Bonn", city))
        .when()
            .post("http://localhost:2000/")
        .then()
            .body(containsString(cityMap.get(city)));

    }
}
