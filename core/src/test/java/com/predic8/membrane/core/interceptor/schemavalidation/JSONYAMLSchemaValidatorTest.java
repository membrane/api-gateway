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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.schemavalidation.json.JSONYAMLSchemaValidator;
import com.predic8.membrane.core.resolver.ClasspathSchemaResolver;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.test.TestAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.schemavalidation.json.JSONYAMLSchemaValidator.SCHEMA_VERSION_2020_12;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void detailsOnByDefault() throws Exception {
        assertEquals(1, invalidAge(validator).get("errors").size());
    }

    @Test
    void validationDetailsOffOmitsErrors() throws Exception {
        JsonNode jn = invalidAge(build(new ErrorDetailsPolicy(false, false)));
        assertEquals("JSON validation failed", jn.get("title").asText());
        assertNull(jn.get("errors"));
        assertNull(jn.get("flow"));
    }

    @Test
    void productionModeKeepsErrors() throws Exception {
        JsonNode jn = invalidAge(build(new ErrorDetailsPolicy(true, true)));
        assertEquals(1, jn.get("errors").size(), "the schema is public, so its errors stay visible in production");
        assertNull(jn.get("attention"), "no development-mode warning on a production router");
    }

    /**
     * Withholding the details from the client must not withhold the failed constraint from the
     * operator: the failure handler is a no-op by default, so the log is the only place left where
     * the reason for the rejection can be found.
     */
    @Test
    void validationDetailsOffStillLogsTheFailedConstraint() throws Exception {
        var appender = captureLog(() -> invalidAge(build(new ErrorDetailsPolicy(false, false))));

        assertTrue(appender.contains("message did not validate against"), appender.getMessages().toString());
        assertTrue(appender.contains("minimum"), appender.getMessages().toString());
    }

    /**
     * What the log must not carry is the message being validated. An {@code additionalProperties}
     * violation reports the whole body as the offending node, so a rejected login would otherwise
     * put the submitted password in the log.
     */
    @Test
    void submittedValuesAreNotLogged() throws Exception {
        var appender = captureLog(() -> {
            Exchange exc = get("/foo").body("""
                    {
                        "name": "Bob",
                        "secret": "hunter2"
                    }
                    """).buildExchange();
            assertEquals(ABORT, build(new ErrorDetailsPolicy(false, false)).validateMessage(exc, REQUEST));
            return null;
        });

        String logged = appender.getMessages().toString();
        assertTrue(logged.contains("additionalProperties"), logged);
        assertFalse(logged.contains("hunter2"), "the submitted value must not be logged: " + logged);
        assertFalse(logged.contains("node="), "the offending node must not be logged: " + logged);
    }

    private static TestAppender captureLog(Callable<?> validation) throws Exception {
        Logger root = (Logger) LogManager.getRootLogger();
        var appender = new TestAppender("JSONYAMLSchemaValidatorTest");
        appender.start();
        root.addAppender(appender);
        try {
            validation.call();
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
        return appender;
    }

    private static JSONYAMLSchemaValidator build(ErrorDetailsPolicy policy) {
        var v = new JSONYAMLSchemaValidator(new ClasspathSchemaResolver(),
                "classpath:/validation/json-schema/simple-schema.json", (a, b) -> {},
                SCHEMA_VERSION_2020_12, policy);
        v.init();
        return v;
    }

    private static JsonNode invalidAge(JSONYAMLSchemaValidator v) throws Exception {
        Exchange exc = get("/foo").body("""
                {
                    "age": -1
                }
                """).buildExchange();
        assertEquals(ABORT, v.validateMessage(exc, REQUEST));
        return new ObjectMapper().readTree(exc.getResponse().getBodyAsStreamDecoded());
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

    @Test
    void malformedJson() throws Exception {
        Exchange exc = get("/foo").body("""
                {
                    "name":
                """).buildExchange();
        assertEquals(ABORT, validator.validateMessage(exc, REQUEST));
        assertEquals(1, validator.getInvalid());
        assertEquals(0, validator.getValid());
    }
}