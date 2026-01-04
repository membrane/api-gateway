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

package com.predic8.membrane.annot.yaml;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.annot.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.annot.yaml.MethodSetter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MethodSetterTest {

    private static final ObjectMapper om = new ObjectMapper();

    @SuppressWarnings("unused")
    static class A {
        public void setA1(B b) {}
        public void setA2(B b) {}
        @MCChildElement
        public void setA3(B b) {}
    }

    @SuppressWarnings("unused")
    static class A2 {
        @MCChildElement
        public void setA1(B b) {}
        @MCChildElement
        public void setA2(B b) {}
    }

    public static class B {}

    public static class C {}

    @Test
    void dontUseMethodsWithoutChildElementAnnotation() {
        MethodSetter ms = getMethodSetter(new ParsingContext("foo", null,
                        new GrammarMock().withGlobalElement("b", B.class)),
                A.class, "b");
        assertEquals("setA3", ms.getSetter().getName());
    }

    @Test
    void multiplePotentialSettersFound() {
        assertThrowsExactly(RuntimeException.class, () -> getMethodSetter(new ParsingContext("foo", null,
                        new GrammarMock().withGlobalElement("b", B.class)),
                A2.class, "b"));
    }

    @Test
    void noPotentialSetterFound() {
        assertThrowsExactly(RuntimeException.class, () -> getMethodSetter(new ParsingContext("foo", null,
                        new GrammarMock().withGlobalElement("c", C.class)),
                A2.class, "c"));
    }

    @Test
    void foo() throws Exception {
        var ms  = new MethodSetter(null, null);
        assertEquals(true, ms.coerceScalarOrReference(null, om.readTree("true"), null, boolean.class));
        assertEquals(true, ms.coerceScalarOrReference(null, om.readTree("true"), null, Boolean.class));
        assertEquals(1, ms.coerceScalarOrReference(null, om.readTree("1"), null, int.class));
        assertEquals(1.0, ms.coerceScalarOrReference(null, om.readTree("1"), null, double.class));
        var l = ms.coerceScalarOrReference(null, om.readTree("1"), null, long.class);
        assertInstanceOf(Long.class, l);
        assertEquals(1L, l);
        assertEquals(true, ms.coerceScalarOrReference(null, om.readTree("true"), null, boolean.class));
    }
}