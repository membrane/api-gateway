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

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import static org.junit.jupiter.api.Assertions.*;

class SchemaValidatorErrorHandlerTest {

    @Test
    void collectsAllErrorsInsteadOfOnlyTheLast() {
        SchemaValidatorErrorHandler handler = new SchemaValidatorErrorHandler();
        SAXParseException first = new SAXParseException("first violation", null);
        SAXParseException second = new SAXParseException("second violation", null);

        handler.error(first);
        handler.error(second);

        assertEquals(2, handler.getExceptions().size());
        assertEquals(first, handler.getExceptions().get(0));
        assertEquals(second, handler.getExceptions().get(1));
    }

    @Test
    void fatalErrorContributesToTheSameListAsError() {
        SchemaValidatorErrorHandler handler = new SchemaValidatorErrorHandler();
        SAXParseException error = new SAXParseException("validity error", null);
        SAXParseException fatal = new SAXParseException("well-formedness error", null);

        handler.error(error);
        handler.fatalError(fatal);

        assertEquals(2, handler.getExceptions().size());
        assertEquals(error, handler.getExceptions().get(0));
        assertEquals(fatal, handler.getExceptions().get(1));
    }

    @Test
    void warningDoesNotCountAsAnError() {
        SchemaValidatorErrorHandler handler = new SchemaValidatorErrorHandler();

        handler.warning(new SAXParseException("just a warning", null));

        assertTrue(handler.noErrors());
        assertTrue(handler.getExceptions().isEmpty());
    }

    @Test
    void noErrorsReflectsWhetherAnyErrorWasReported() {
        SchemaValidatorErrorHandler handler = new SchemaValidatorErrorHandler();

        assertTrue(handler.noErrors());

        handler.error(new SAXParseException("violation", null));

        assertFalse(handler.noErrors());
    }

    @Test
    void resetClearsAccumulatedErrorsForTheNextBorrowedUse() {
        SchemaValidatorErrorHandler handler = new SchemaValidatorErrorHandler();
        handler.error(new SAXParseException("first use violation", null));
        handler.error(new SAXParseException("first use violation 2", null));

        handler.reset();

        assertTrue(handler.noErrors());
        assertTrue(handler.getExceptions().isEmpty());
    }
}
