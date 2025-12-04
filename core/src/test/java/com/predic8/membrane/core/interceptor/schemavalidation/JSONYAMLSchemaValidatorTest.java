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

package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.schemavalidation.json.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JSONYAMLSchemaValidatorTest {

    JSONYAMLSchemaValidator validator;

    @BeforeEach
    void setup() {
        validator = new JSONYAMLSchemaValidator(new ClasspathSchemaResolver(), "classpath:/validation/json-schema/simple-schema.json", (a,b) -> {});
        validator.init();
    }

    @Test
    void invalidSchemaVersion() {
        assertThrows(ConfigurationException.class, () ->
                new JSONYAMLSchemaValidator(new ClasspathSchemaResolver(), "doesn't matter", null,"unknown version"));
    }

    @Test
    void simple() throws Exception {
        Exchange exc = get("/foo").body("""
                {
                    "name": "Robert"
                }
                """).buildExchange();
        assertEquals(CONTINUE, validator.validateMessage( exc, REQUEST));
        assertEquals(1, validator.getValid());
    }

    @Test
    void invalidNumber() throws Exception {
        Exchange exc = get("/foo").body("""
                {
                    "age": -1
                }
                """).buildExchange();
        validator.validateMessage( exc, REQUEST);
        assertEquals(1, validator.getInvalid());
        assertEquals(0, validator.getValid());
    }

    @Test
    void additionalProperty() throws Exception {
        Exchange exc = get("/foo").body("""
                {
                    "unknown": "foo"
                }
                """).buildExchange();
        validator.validateMessage( exc, REQUEST);
    }
}