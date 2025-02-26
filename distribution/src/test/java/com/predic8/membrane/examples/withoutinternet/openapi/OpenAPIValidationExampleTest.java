/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.withoutinternet.openapi;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.test.HttpAssertions;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.XML;

public class OpenAPIValidationExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "openapi/validation";
    }

    @Test
    public void validRequest() throws Exception {
        try (HttpAssertions ha = new HttpAssertions()) {
            ha.getAndAssert(200, LOCALHOST_2000 + "/demo-api/v2/persons?limit=10");
        }
    }

    @Test
    void oneOfWithRightInteger() {
        // @formatter:off
        given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "Jan Vermeer",
                        "countryCode": "DE",
                        "address": {
                            "city": "Bonn",
                            "street": "Koblenzer Straße 65",
                            "zip": 53173
                        }
                    }""")
        .when()
                .put(LOCALHOST_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1")
        .then().assertThat()
                .statusCode(201);
        // @formatter:on
    }

    @Test
    void oneOfWithWrongStringPattern() throws JSONException {
        // @formatter:off
        Response res = given()
            .contentType(JSON)
            .body("""
                {
                    "name": "Jan Vermeer",
                    "countryCode": "DE",
                    "address": {
                        "city": "Bonn",
                        "street": "Koblenzer Straße 65",
                        "zip": "D-5317"
                    }
                }"""
            )
        .when()
            .put(LOCALHOST_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1");

        res.then().assertThat()
            .statusCode(400);

        JSONAssert.assertEquals("""
                {
                  "title": "OpenAPI message validation failed",
                  "type": "https://membrane-api.io/problems/user/validation",
                  "validation": {
                      "method" : "PUT",
                      "uriTemplate" : "/persons/{pid}",
                      "path" : "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1",
                      "errors" : {
                        "REQUEST/BODY#/address/zip" : [ {
                          "message" : "OneOf requires that exactly one subschema is valid. But there are 0 subschemas valid.",
                          "complexType" : "Address",
                          "schemaType" : "object"
                        } ]
                      }
                  }
                }""", res.asString(), false);
        // @formatter:on
    }

    @Test
    void nestedObject() {
        // @formatter:off
        given()
            .contentType(APPLICATION_JSON)
            .body("""
                    {
                    	"name": "Jan Vermeer",
                    	"countryCode": "DE",
                    	"address": {
                    		"city": "Bonn",
                    		"street": "Koblenzer Straße 65",
                    		"zip": "D-53173"
                    	}
                    }
                    """)
        .put(LOCALHOST_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1")
        .then().assertThat()
            .statusCode(201);
        // @formatter:on
    }

    @Test
    void wrongRegexPattern() throws JSONException {
        // @formatter:off
        Response res = given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "Jan Vermeer",
                        "countryCode": "Germany"
                    }"""
                )
                .when()
                .put(LOCALHOST_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1");

        res.then().assertThat()
                .statusCode(400);

        JSONAssert.assertEquals("""
                {
                   "validation": {
                       "method" : "PUT",
                       "uriTemplate" : "/persons/{pid}",
                       "path" : "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1",
                       "errors" : {
                         "REQUEST/BODY#/countryCode" : [ {
                           "message" : "The string 'Germany' is 7 characters long. MaxLength of 2 is exceeded.",
                           "complexType" : "Person",
                           "schemaType" : "string"
                         }, {
                           "message" : "The string 'Germany' does not match the regex pattern \\\\w{2}.",
                           "complexType" : "Person",
                           "schemaType" : "string"
                         } ]
                       }
                   }
                 }""", res.asString(), false);
        // @formatter:on
    }

    @Test
    void additionalPropertyRole() throws JSONException {
        // @formatter:off
        Response res = given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "Jan Vermeer",
                        "role": "admin"
                    }"""
                )
                .when()
                .put(LOCALHOST_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1");

        res.then().assertThat()
                .statusCode(400);

        JSONAssert.assertEquals("""
                {
                  "validation": {
                      "method" : "PUT",
                      "uriTemplate" : "/persons/{pid}",
                      "path" : "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1",
                      "errors" : {
                        "REQUEST/BODY" : [ {
                          "message" : "The object has the additional Property: role .But the schema does not allow additional properties.",
                          "complexType" : "Person",
                          "schemaType" : "object"
                        } ]
                      }
                  }
                }""", res.asString(), false);
        // @formatter:on
    }

    @Test
    void RequiredPropertyIsMissing() throws JSONException {
        // @formatter:off
        Response res = given()
                .contentType(JSON)
                .body("""
                    {
                      "email": "jan@predic8.de"
                    }"""
                )
                .when()
                .put(LOCALHOST_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1");

        res.then().assertThat()
                .statusCode(400);

        JSONAssert.assertEquals("""
                {
                  "validation": {
                      "method" : "PUT",
                      "uriTemplate" : "/persons/{pid}",
                      "path" : "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1",
                      "errors" : {
                        "REQUEST/BODY#/name" : [ {
                          "message" : "Required property name is missing.",
                          "complexType" : "Person",
                          "schemaType" : "object"
                        } ]
                      }
                  }
                }""", res.asString(), false);
        // @formatter:on
    }

    @Test
    void wrongContentType() {
        // @formatter:off
        given()
            .contentType(XML)
            .body("<name>Jan</name>")
        .when()
            .put(LOCALHOST_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1")
        .then().assertThat()
            .statusCode(415);
        // @formatter:on
    }

    @Test
    void invalidUUIDEmailAndEnum() throws JSONException {
        // @formatter:off
        Response res = given()
                .contentType(JSON)
                .body("""
                    {
                    	"name": "Jan Vermeer",
                    	"email": "jan(at)schilderei.nl",
                    	"type": "ARTIST"
                    }"""
                )
                .when()
                .put(LOCALHOST_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2D-FC3112CE89D1");

        res.then().assertThat()
                .statusCode(400);

        JSONAssert.assertEquals("""
                {
                  "validation": {
                      "method" : "PUT",
                      "uriTemplate" : "/persons/{pid}",
                      "path" : "/demo-api/v2/persons/4077C19D-2C1D-427B-B2D-FC3112CE89D1",
                      "errors" : {
                        "REQUEST/BODY#/type" : [ {
                          "message" : "The string 'ARTIST' does not contain a value from the enum PRIVAT,BUSINESS.",
                          "complexType" : "Person",
                          "schemaType" : "string"
                        } ],
                        "REQUEST/BODY#/email" : [ {
                          "message" : "The string 'jan(at)schilderei.nl' is not a valid email.",
                          "complexType" : "Person",
                          "schemaType" : "string"
                        } ],
                        "REQUEST/PATH_PARAMETER/pid" : [ {
                          "message" : "The string '4077C19D-2C1D-427B-B2D-FC3112CE89D1' is not a valid UUID.",
                          "schemaType" : "string"
                        } ]
                	}
                  }
                }""", res.asString(), false);
        // @formatter:on
    }

    @Test
    void validPut() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("""
                {
                    "name": "Jan Vermeer"
                }""")
        .when()
            .put(LOCALHOST_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1")
        .then().assertThat()
            .statusCode(201);
        // @formatter:on
    }

    @Test
    void limitGreaterThan100() throws JSONException {
        // @formatter:off
        Response res = given()
            .contentType(JSON)
        .when()
            .get(LOCALHOST_2000 + "/demo-api/v2/persons?limit=200");

        res.then().assertThat()
            .statusCode(400);

        JSONAssert.assertEquals("""
                {
                    "validation": {
                        "method" : "GET",
                        "uriTemplate" : "/persons",
                        "path" : "/demo-api/v2/persons?limit=200",
                        "errors" : {
                          "REQUEST/QUERY_PARAMETER/limit" : [ {
                            "message" : "200.0 is greater than the maximum of 100",
                            "schemaType" : "integer"
                          } ]
                        }
                	}
                  }
                """, res.asString(), false);
        // @formatter:on
    }

    @Test
    void wrongPath() throws JSONException {
        // @formatter:off
        Response res = given()
                .contentType(JSON)
                .when()
                .get(LOCALHOST_2000 + "/demo-api/v2/wrong");

        res.then().assertThat()
                .statusCode(404);

        JSONAssert.assertEquals("""
                {
                    "validation": {
                        "method" : "GET",
                        "path" : "/demo-api/v2/wrong",
                        "errors" : {
                          "REQUEST/PATH" : [ {
                            "message" : "Path /demo-api/v2/wrong is invalid."
                          } ]
                        }
                	}
                 }
                """, res.asString(), false);
        // @formatter:on
    }
}
