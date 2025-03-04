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
package com.predic8.membrane.core.graphql;

import com.google.common.collect.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

public class GraphQLoverHttpValidatorTest {

    GraphQLoverHttpValidator validator1;

    @BeforeEach
    void setup() {
        Router router = new Router();
        validator1 = new GraphQLoverHttpValidator(false, Lists.newArrayList("GET", "POST"), 3, 3, 2, null, router);
    }

    @Test
    void validate() {
        assertDoesNotThrow(() -> validator1.validate(getExchange("""
           {"query":"{a}"}""")));
    }

    @Test
    void wrongOperation() {
        try {
            validator1.validate(getExchange("""
                {
                    "query": "{a}",
                    "operationName": 5
                }"""));
        } catch (GraphQLOverHttpValidationException e) {
            assertEquals("Expected 'operationName' to be a String.", e.getMessage());
            return;
        }
        fail();
    }


    @Test
    public void depthNotOK() {
        try {
            validator1.validate(getExchange("""
                 {"query":"{ a { b { c { d { e { f { g { h } } } } } } } }",
                        "operationName": ""}"""));
        } catch (GraphQLOverHttpValidationException e) {
            assertEquals("Max depth exceeded.", e.getMessage());
            return;
        }
        fail();
    }

    private static Exchange getExchange(String body) {
        try {
            return new Request.Builder().post("/graphql").body(body).contentType(APPLICATION_JSON).buildExchange();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}