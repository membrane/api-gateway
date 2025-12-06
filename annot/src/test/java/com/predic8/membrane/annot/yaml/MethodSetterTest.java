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

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.util.GrammarMock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MethodSetterTest {

    public class A {
        public void setA1(B b) {}
        public void setA2(B b) {}
        @MCChildElement
        public void setA3(B b) {}
    }

    public class A2 {
        @MCChildElement
        public void setA1(B b) {}
        @MCChildElement
        public void setA2(B b) {}
    }

    public static class B {}

    public static class C {}

    @Test
    public void dontUseMethodsWithoutChildElementAnnotation() {
        MethodSetter ms = MethodSetter.getMethodSetter(new ParsingContext("foo", null,
                        new GrammarMock().withGlobalElement("b", B.class)),
                A.class, "b");
        assertEquals("setA3", ms.getSetter().getName());
    }

    @Test
    public void multiplePotentialSettersFound() {
        assertThrowsExactly(RuntimeException.class, () -> {
            MethodSetter.getMethodSetter(new ParsingContext("foo", null,
                            new GrammarMock().withGlobalElement("b", B.class)),
                    A2.class, "b");
        });
    }

    @Test
    public void noPotentialSetterFound() {
        assertThrowsExactly(RuntimeException.class, () -> {
            MethodSetter.getMethodSetter(new ParsingContext("foo", null,
                            new GrammarMock().withGlobalElement("c", C.class)),
                    A2.class, "c");
        });
    }

}