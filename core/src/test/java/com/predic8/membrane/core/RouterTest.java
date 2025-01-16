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
package com.predic8.membrane.core;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import io.restassured.*;
import io.restassured.response.*;
import org.hamcrest.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.parsers.*;
import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class RouterTest {

    public static final String INTERNAL_SECRET = "supersecret";
    static Router dev, prod;
    
    @BeforeAll
    static void setUp() throws IOException {
        prod = createRouter(2000, true);
        dev = createRouter(2001, false);
    }

    @AfterAll
    static void tearDown() {
        prod.shutdown();
        dev.shutdown();
    }

    @Test
    void prodJson() {

        // @formatter:off
        ExtractableResponse<Response> r = RestAssured.given()
            .contentType(APPLICATION_JSON)
            .post("http://localhost:2000/")
        .then()
            .statusCode(500)
            .contentType(APPLICATION_PROBLEM_JSON)
            .body("title", equalTo("An error occurred."))
            .body("type",equalTo("https://membrane-api.io/error/internal"))
            .body("detail",containsString("key"))
            .body("$",aMapWithSize(3))
        .extract();
        // @formatter:on

//        System.out.println("r = " + r.asPrettyString());
    }

    @Test
    void prodXML() throws Exception {
        // @formatter:off
        ExtractableResponse<Response> r  = RestAssured.given()
            .contentType(APPLICATION_XML)
            .post("http://localhost:2000/")
        .then()
            .statusCode(500)
            .contentType(APPLICATION_XML)
            .body("error.title", equalTo("An error occurred."))
            .body("error.type",equalTo("https://membrane-api.io/error/internal"))
            .body("error.message", Matchers.not(containsString(INTERNAL_SECRET)))
            .body("error.detail",containsString("key"))
            .extract();
        // @formatter:on

//        System.out.println("r.asPrettyString() = " + r.asPrettyString());
        
        NodeList n = getNodeList(r.asInputStream());
        assertEquals(3, n.getLength());
    }

    @Test
    void  devJson() {
        // @formatter:off
        ExtractableResponse<Response> r = RestAssured.given()
            .get("http://localhost:2001/")
        .then()
            .statusCode(500)
            .contentType(APPLICATION_PROBLEM_JSON)
            .body("title", equalTo("Internal server error."))
            .body("type",equalTo("https://membrane-api.io/error/internal"))
            .body("$",hasKey("attention"))
            .body("attention", Matchers.containsString("development mode"))
            .body("$",not(hasKey("stacktrace")))
            .extract();
        // @formatter:on

//        System.out.println("r = " + r.asPrettyString());
                
    }

    @Test
    void devXML() throws Exception {
        // @formatter:off
        ExtractableResponse<Response> r = RestAssured.given()
                .contentType(APPLICATION_XML)
                .post("http://localhost:2001/")
            .then()
                .statusCode(500)
//                .contentType(APPLICATION_XML)
//                .body("error.title", equalTo("Internal server error."))
//                .body("error.type",equalTo("https://membrane-api.io/error/internal"))
//                .body("error.attention", Matchers.containsString("development mode"))
                .extract();
        // @formatter:on

        System.out.println(r.asPrettyString());
        NodeList n = getNodeList(r.asInputStream());
        assertEquals(7, n.getLength());
    }

    private static Router createRouter(int port, boolean production) throws IOException {
        HttpRouter r = new HttpRouter();
        r.setProduction(production);
        APIProxy api = new APIProxy();
        api.setKey(new APIProxyKey(port));
        api.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                throw new RuntimeException(INTERNAL_SECRET);
            }
        });
        r.setHotDeploy(false);
        r.add(api);
        r.start();
        return r;
    }

    private static @NotNull NodeList getNodeList(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(is);
        return document.getDocumentElement().getChildNodes();
    }

}