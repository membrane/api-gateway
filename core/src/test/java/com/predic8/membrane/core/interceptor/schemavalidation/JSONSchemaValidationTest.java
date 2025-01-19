/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.schemavalidation;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.resolver.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

public class JSONSchemaValidationTest {

    private static final ObjectMapper om = new ObjectMapper();

    @Test
    void valid1() throws Exception {
        assertEquals(CONTINUE, getValidator("""
                {
                    "required": [ "p1" ],
                    "properties": {
                        "p1": {
                            "format": "date"
                        },
                        "p2": {
                            "format": "phone"
                        }
                    }
                }
                """).validateMessage(post("/foo").body("""
                {
                    "p1": "2010-11-15",
                    "p2": "+3927166273"
                }
                """).buildExchange(), REQUEST));
    }

    @Test
    void inValid1() throws Exception {
        var validator = getValidator("""
                {
                    "required": [ "p1" ],
                    "properties": {
                        "p1": {
                            "format": "date"
                        },
                        "p2": {
                            "format": "phone"
                        }
                    }
                }
                """);
        Exchange exc = post("/foo").body("""
                {
                  	"p2": null
                }
                """).buildExchange();
        assertEquals(ABORT, validator.validateMessage(exc, REQUEST));

        JsonNode jn =  om.readTree(exc.getResponse().getBodyAsStream());

        assertEquals("JSON validation failed", jn.get("title").textValue());
        assertEquals("https://membrane-api.io/error/user",jn.get("type").textValue());
        assertEquals(1, jn.get("errors").size());

//        System.out.println("exc.getResponse().getBodyAsStringDecoded() = " + exc.getResponse().getBodyAsStringDecoded());
    }

    @Test
    void valid2() throws Exception {
        assertEquals(CONTINUE, getValidator("""
              {
                 "required": ["id","price"],
                 "properties": {
                     "id": {
                         "type": "integer"
                     },
                     "price": {
                         "type": "number",
                                 "minimum": 0
                     },
                     "tags": {
                         "type": "array",
                         "items": {
                             "type": "string"
                         }
                     }
                 }
             }
             """).validateMessage(post("/foo").body("""
                {
                    "id": 123,
                    "price": 1.99,
                    "tags": ["food","fresh"]
                }
                """).buildExchange(), REQUEST));
    }

    @Test
    void inValid2() throws Exception {
        var exc = post("/foo").body("""
                {
                    "id": "123",
                    "price": -1.99,
                    "tags": ["food","fresh",2]
                }
                """).buildExchange();
        assertEquals(ABORT, getValidator("""
              {
                 "required": ["id","price"],
                 "properties": {
                     "id": {
                         "type": "integer"
                     },
                     "price": {
                         "type": "number",
                                 "minimum": 0
                     },
                     "tags": {
                         "type": "array",
                         "items": {
                             "type": "string"
                         }
                     }
                 }
             }
             """).validateMessage(exc, REQUEST));

        JsonNode jn =  om.readTree(exc.getResponse().getBodyAsStream());

        assertEquals("JSON validation failed", jn.get("title").textValue());
        assertEquals("https://membrane-api.io/error/user",jn.get("type").textValue());
        assertEquals(2, jn.get("errors").size());

        System.out.println("exc.getResponse().getBodyAsStringDecoded() = " + exc.getResponse().getBodyAsStringDecoded());
    }

    private static @NotNull JSONSchemaValidator getValidator(String schema) {
        var validator = new JSONSchemaValidator(new StaticStringResolver(), schema, null);
        validator.init();
        return validator;
    }
}
