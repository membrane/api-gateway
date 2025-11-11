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

import com.networknt.schema.InputFormat;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.schemavalidation.json.JSONYAMLSchemaValidator;
import com.predic8.membrane.core.resolver.ClasspathSchemaResolver;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.networknt.schema.InputFormat.YAML;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.schemavalidation.json.JSONYAMLSchemaValidator.SCHEMA_VERSION_2020_12;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JSONYAMLSchemaValidatorYAMLTest {

    JSONYAMLSchemaValidator validator;

    @BeforeEach
    void setup() {
        validator = new JSONYAMLSchemaValidator(new ClasspathSchemaResolver(), "/validation/json-schema/simple-schema.json", (a,b) -> {}, SCHEMA_VERSION_2020_12, YAML);
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
                "name": "Robert"
                """).buildExchange();
        assertEquals(CONTINUE, validator.validateMessage( exc, REQUEST));
        assertEquals(1, validator.getValid());
    }

    @Test
    void invalidNumber() throws Exception {
        Exchange exc = get("/foo").body("""
                "age": -1
                """).buildExchange();
        validator.validateMessage( exc, REQUEST);
        assertEquals(1, validator.getInvalid());
        assertEquals(0, validator.getValid());
    }

    @Test
    void additionalProperty() throws Exception {
        Exchange exc = get("/foo").body("""
                "unknown": "foo"
                """).buildExchange();
        validator.validateMessage( exc, REQUEST);
        assertEquals(1, validator.getInvalid());
    }

    @Test
    void multipleInvalidYAMLDocuments() throws Exception {
        Exchange exc = get("/foo").body("""
                "name": "Robert"
                ---
                "age": -1
                """).buildExchange();
        validator.validateMessage( exc, REQUEST);
        assertEquals(1, validator.getInvalid());
        assertEquals(0, validator.getValid());
    }

    @Test
    void multipleYAMLDocuments() throws Exception {
        Exchange exc = get("/foo").body("""
                "name": "Robert"
                ---
                "name": "Robert"
                """).buildExchange();
        validator.validateMessage( exc, REQUEST);
        assertEquals(1, validator.getValid());
    }
}