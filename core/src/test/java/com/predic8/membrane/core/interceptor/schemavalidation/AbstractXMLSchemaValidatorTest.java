/* Copyright 2026 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.test.TestAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbstractXMLSchemaValidator#validateAgainstSchemas} is meant to try every embedded schema
 * until one matches. If running one schema's validator throws (as opposed to reporting an ordinary
 * validation error through its {@link SchemaValidatorErrorHandler}), the remaining schemas must
 * still get a chance, and the failure must be logged - unlike an ordinary per-schema mismatch, it
 * signals something more serious (e.g. a schema failing to resolve an import).
 */
class AbstractXMLSchemaValidatorTest {

    private Logger root;
    private TestAppender appender;

    @BeforeEach
    void attachAppender() {
        root = (Logger) LogManager.getRootLogger();
        appender = new TestAppender("AbstractXMLSchemaValidatorTest");
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        root.removeAppender(appender);
        appender.stop();
    }

    @Test
    void oneSchemaThrowingDoesNotStopTheOthersFromBeingTriedAndIsLogged() throws Exception {
        var validator = new TestValidator();
        validator.init();

        var exceptions = new ArrayList<Exception>();
        // The first embedded schema's validator run always throws; the underlying stream errors
        // out instead of reporting a validation failure. The second schema, tried on a fresh
        // Source, matches "<ok/>" cleanly.
        var calls = new AtomicInteger();
        Supplier<Source> source = () -> calls.incrementAndGet() == 1 ? failingSource() : okSource();

        assertTrue(validator.validateAgainstSchemas(source, exceptions),
                "the second schema should still have matched: " + exceptions);
        assertTrue(appender.contains("threw while validating"),
                "a validator throwing must be logged even though another schema went on to match: "
                        + appender.getMessages());
    }

    private static Source failingSource() {
        return new StreamSource(new InputStream() {
            public int read() throws IOException {
                throw new IOException("simulated I/O failure reading the first schema's source");
            }
        });
    }

    private static Source okSource() {
        return new StreamSource(new StringReader("<ok/>"));
    }

    private static Validator newValidator(String schemaXml) throws Exception {
        var sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        var validator = sf.newSchema(new StreamSource(new StringReader(schemaXml))).newValidator();
        validator.setErrorHandler(new SchemaValidatorErrorHandler());
        return validator;
    }

    private static class TestValidator extends AbstractXMLSchemaValidator {

        TestValidator() {
            super(new ResolverMap(), "test-location", null);
        }

        @Override
        public String getName() {
            return "test-validator";
        }

        @Override
        protected List<Validator> createValidators() {
            try {
                // Content is irrelevant: the first validator's run always throws before it ever
                // gets to parse anything.
                var throwsOnRun = newValidator("""
                        <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                          <xsd:element name="ok"/>
                        </xsd:schema>
                        """);
                var matchesOk = newValidator("""
                        <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                          <xsd:element name="ok"/>
                        </xsd:schema>
                        """);
                return List.of(throwsOnRun, matchesOk);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected List<Element> getSchemas() {
            throw new UnsupportedOperationException("createValidators() is overridden for this test");
        }

        @Override
        protected Source getMessageBody(InputStream input) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        protected void setErrorResponse(Exchange exchange, String message) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        protected void setErrorResponse(Exchange exchange, Flow flow, List<Exception> exceptions) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        protected String getPreliminaryError(XOPReconstitutor xopr, Message msg) {
            throw new UnsupportedOperationException("not used by this test");
        }
    }
}
