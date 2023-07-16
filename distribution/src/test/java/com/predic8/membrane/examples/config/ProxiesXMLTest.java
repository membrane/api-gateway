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

package com.predic8.membrane.examples.config;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.*;

import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class ProxiesXMLTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "..";
    }

    @SuppressWarnings("JsonSchemaCompliance")
    @Test
    void api_doc() {
        JSONAssert.assertEquals("""
                {
                  "fruitshop-v1-0" : {
                    "openapi" : "3.0.2",
                    "title" : "Fruit Shop API",
                    "version" : "1.0",
                    "openapi_link" : "/api-doc/fruitshop-v1-0",
                    "ui_link" : "/api-doc/ui/fruitshop-v1-0"
                  }
                }
                """, get(LOCALHOST_2000 + "/api-doc").asString(), true);
    }

    @Test
    void postLikeSwaggerUI() {
        given()
                .contentType(APPLICATION_JSON)
                .body("""
                        {
                             "name": "Figs",
                             "price": 2.7
                         }
                        """)
        .when()
                .post(LOCALHOST_2000 + "/shop/products/")
        .then().assertThat()
                .statusCode(201)
                .contentType(APPLICATION_JSON)
                .body("name", equalTo("Figs"))
                .body("price", equalTo(2.7F));
    }

    @Test
    public void names() {
        get(LOCALHOST_2000 + "/names/Pia")
                .then()
                .assertThat()
                .statusCode(200)
                .contentType(APPLICATION_JSON)
                .body("restnames.nameinfo.name", equalTo("Pia"));

        get(LOCALHOST_2000 + "/names/Pia")
                .then()
                .assertThat()
                .statusCode(200);

        get(LOCALHOST_2000 + "/names/Pia")
                .then()
                .assertThat()
                .statusCode(200);

        get(LOCALHOST_2000 + "/names/Pia")
                .then()
                .assertThat()
                .statusCode(429);
    }

    @Test
    public void groovy() {
        AtomicBoolean headingFound = new AtomicBoolean();
        AtomicBoolean hostFound = new AtomicBoolean();
        process.addConsoleWatcher((error, line) -> {
            if (line.contains("Request headers"))
                headingFound.set(true);
            if (line.contains("Host: localhost:2000"))
                hostFound.set(true);
        });

        get(LOCALHOST_2000 + "/header")
        .then().assertThat()
                .contentType(APPLICATION_JSON)
                .body("ok", equalTo(1));

        assertTrue(headingFound.get());
        assertTrue(hostFound.get());
    }

    @Test
    public void normalAPI() {
       get(LOCALHOST_2000)
               .then().assertThat()
               .statusCode(200)
               .contentType(APPLICATION_JSON)
               .body("apis[0]['name']",equalTo("Shop API Showcase"));
    }

    @Test
    void adminConsole() {
        // @formatter:off
        given().
        when().
                get("http://localhost:9000/admin").
        then().assertThat()
                .statusCode(200)
                .contentType(TEXT_HTML)
                .body(containsString("Administration"));
        // @formatter:on
    }
}