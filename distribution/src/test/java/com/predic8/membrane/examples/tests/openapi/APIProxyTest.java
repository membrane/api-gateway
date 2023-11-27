/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.tests.openapi;

import com.predic8.membrane.examples.util.*;
import io.restassured.response.*;
import org.hamcrest.*;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.*;

import java.io.*;

import static com.predic8.membrane.test.AssertUtils.*;
import static io.restassured.RestAssured.*;
import static io.restassured.http.ContentType.*;

public class APIProxyTest extends AbstractSampleMembraneStartStopTestcase  {

    final String[] ACCEPT_HTML_HEADER = {"Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"};

    @Override
    protected String getExampleDirName() {
        return "openapi/openapi-proxy";
    }

    @SuppressWarnings("JsonSchemaCompliance")
    @Test
    void api_doc() throws IOException {
        String andAssert = getAndAssert(200, LOCALHOST_2000 + "/api-docs");
        JSONAssert.assertEquals("""
                {
                  "fruitshop-v2-0" : {
                    "openapi" : "3.0.2",
                    "title" : "Fruit Shop API",
                    "version" : "2.0",
                    "openapi_link" : "/api-docs/fruitshop-v2-0",
                    "ui_link" : "/api-docs/ui/fruitshop-v2-0"
                  }
                }
                """, andAssert, true);
    }

    @Test
    void apiOverview() throws IOException {
        String body = getAndAssert(200, LOCALHOST_2000 + "/api-doc", ACCEPT_HTML_HEADER);
        assertContains("""
                <h1 class="title">APIs</h1>
                """, body);
        assertContains("Fruit Shop API", body);
        assertContains("/shop", body);
    }

    @Test
    void swaggerUi() throws IOException {
        String body = getAndAssert(200, LOCALHOST_2000 + "/api-docs/ui/fruitshop-v2-0", ACCEPT_HTML_HEADER);
        assertContains("""
                content="SwaggerUI""", body);
        assertContains("/api-docs/fruitshop-v2-0", body);
    }

    @Test
    void postLikeSwaggerUI() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("""
                    {
                         "name": "Figs",
                         "price": 2.7
                    }
                """)
        .when()
            .post(LOCALHOST_2000 + "/shop/v2/products")
        .then()
                .statusCode(201)
                .body("name", Matchers.equalTo("Figs"))
                .body("price", Matchers.equalTo(2.7F));
        // @formatter:on
    }

    @Test
    void postLikeSwaggerUIInvalidPrice() {
        // @formatter:off
        Response res = given()
            .contentType(JSON)
            .body("""
                {
                     "name": "Figs",
                     "price": -2.7
                }
            """)
        .when()
            .post(LOCALHOST_2000 + "/shop/v2/products");
        // @formatter:on

        res.then().assertThat().statusCode(400);

        JSONAssert.assertEquals("""
                        {
                          "method" : "POST",
                          "uriTemplate" : "/products",
                          "path" : "/shop/v2/products",
                          "validationErrors" : {
                            "REQUEST/BODY#/price" : [ {
                              "message" : "-2.7 is smaller than the minimum of 0",
                              "complexType" : "Product",
                              "schemaType" : "number"
                            } ]
                          }
                        }
                        """
                , res.body().asString(), true);
    }
}
