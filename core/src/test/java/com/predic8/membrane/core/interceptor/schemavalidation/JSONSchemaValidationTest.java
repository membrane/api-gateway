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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.resolver.StaticStringResolver;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.FailureHandler.VOID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals("https://membrane-api.io/problems/user/validation",jn.get("type").textValue());
        assertEquals(1, jn.get("errors").size());
    }

    @Test
    void malformedJson() throws Exception {
        var validator = getValidator("""
                {
                    "required": [ "p1" ],
                    "properties": {
                        "p1": {
                            "format": "date"
                        }
                    }
                }
                """);
        Exchange exc = post("/foo").body("{ invalid").buildExchange();
        assertEquals(ABORT, validator.validateMessage(exc, REQUEST));

        JsonNode jn = om.readTree(exc.getResponse().getBodyAsStream());

        assertEquals("JSON validation failed", jn.get("title").textValue());
        assertEquals("https://membrane-api.io/problems/user/validation", jn.get("type").textValue());
        assertEquals(1, jn.get("errors").size());
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
        assertEquals("https://membrane-api.io/problems/user/validation",jn.get("type").textValue());
        assertEquals(2, jn.get("errors").size());
    }

    /**
     * No caller ever constructs a validator with the {@code FailureHandler.VOID} singleton itself
     * (by reference) - a validator built with any other {@link ValidatorInterceptor.FailureHandler},
     * VOID included, must go through the ordinary failure-reporting path: invoke the handler and
     * still build a ProblemDetails response, rather than silently setting an exchange property and
     * nothing else.
     */
    @Test
    void voidFailureHandlerStillReportsFailure() throws Exception {
        var validator = new JSONSchemaValidator(new StaticStringResolver(), """
                {
                    "required": [ "p1" ]
                }
                """, VOID);
        validator.init();

        Exchange exc = post("/foo").body("{}").buildExchange();
        assertEquals(ABORT, validator.validateMessage(exc, REQUEST));

        JsonNode jn = om.readTree(exc.getResponse().getBodyAsStream());
        assertEquals("JSON validation failed", jn.get("title").textValue());
        assertEquals(1, jn.get("errors").size(), "a failure handler must not swallow the validation details");
    }

    @Test
    void validationDetailsOffOmitsErrors() throws Exception {
        var validator = new JSONSchemaValidator(new StaticStringResolver(), """
                {
                    "required": [ "p1" ]
                }
                """, VOID, new ErrorDetailsPolicy(false, false));
        validator.init();

        Exchange exc = post("/foo").body("{}").buildExchange();
        assertEquals(ABORT, validator.validateMessage(exc, REQUEST));

        JsonNode jn = om.readTree(exc.getResponse().getBodyAsStream());
        assertEquals("JSON validation failed", jn.get("title").textValue());
        assertEquals("https://membrane-api.io/problems/user/validation", jn.get("type").textValue());
        assertNull(jn.get("errors"));
        assertNull(jn.get("flow"));
    }

    @Test
    void productionModeKeepsErrors() throws Exception {
        var validator = new JSONSchemaValidator(new StaticStringResolver(), """
                {
                    "required": [ "p1" ]
                }
                """, VOID, new ErrorDetailsPolicy(true, true));
        validator.init();

        Exchange exc = post("/foo").body("{}").buildExchange();
        assertEquals(ABORT, validator.validateMessage(exc, REQUEST));

        JsonNode jn = om.readTree(exc.getResponse().getBodyAsStream());
        assertEquals(1, jn.get("errors").size(), "the schema is public, so its errors stay visible in production");
        assertNull(jn.get("attention"), "no development-mode warning on a production router");
    }

    private static @NotNull JSONSchemaValidator getValidator(String schema) {
        var validator = new JSONSchemaValidator(new StaticStringResolver(), schema, null);
        validator.init();
        return validator;
    }
}
