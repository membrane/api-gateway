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
package com.predic8.membrane.core.lang.groovy;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("rawtypes")
class GroovyExchangeExpressionTest extends AbstractExchangeExpressionTest {

    @Override
    protected Request.Builder getRequestBuilder() throws URISyntaxException {
        return Request.get("foo");
    }

    @Override
    protected Language getLanguage() {
        return GROOVY;
    }

    @Test
    void string() {
        assertEquals("Jelly Fish", evalString("header.getFirstValue('name')"));
    }

    @Test
    void accessNonExistingPropertyAsObject() {
        assertNull(evalObject("property.unknown"));
    }

    @Test
    void accessNonExistingPropertyAsString() {
        assertEquals("",evalString("property.unknown"));
    }

    @Test
    void truth() {
        assertTrue(evalBool("property.wet"));
        assertFalse(evalBool("property['can-fly']"));
    }

    @Test
    void list() {
        Object o = evalObject("property.tags");
        if (!(o instanceof List l)) {
            fail();
            return;
        }
        assertEquals(2, l.size());
        assertEquals("animal", l.get(0));
        assertEquals("water", l.get(1));
    }

    @Test
    void map() {
        Object o = evalObject("property.world");
        if (!(o instanceof Map m)) {
            fail();
            return;
        }
        assertEquals("US",m.get("country"));
        assertEquals("Europe",m.get("continent"));
    }
}