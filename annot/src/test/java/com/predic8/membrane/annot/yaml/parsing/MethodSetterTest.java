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

package com.predic8.membrane.annot.yaml.parsing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCOtherAttributes;
import com.predic8.membrane.annot.util.GrammarMock;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.annot.yaml.ParsingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Stream;

import static com.predic8.membrane.annot.yaml.parsing.MethodSetter.getMethodSetter;
import static org.junit.jupiter.api.Assertions.*;

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

    private static final String OTHER_ATTRIBUTE_NAME = "timeout";

    @SuppressWarnings("unused")
    static class IntegerAttributes {
        @MCOtherAttributes
        public void setAttributes(Map<String, Integer> attributes) {}
    }

    @SuppressWarnings("unused")
    static class EnumAttributes {
        @MCOtherAttributes
        public void setAttributes(Map<String, Mode> attributes) {}
    }

    @SuppressWarnings("unused")
    static class StringAttributes {
        @MCOtherAttributes
        public void setAttributes(Map<String, String> attributes) {}
    }

    enum Mode {
        FAST
    }

    @Test
    void dontUseMethodsWithoutChildElementAnnotation() {
        MethodSetter ms = getMethodSetter(new ParsingContext("foo",null,
                        new GrammarMock().withGlobalElement("b", B.class),null, null,null),
                A.class, "b");
        assertEquals("setA3", ms.getSetter().getName());
    }

    @Test
    void multiplePotentialSettersFound() {
        assertThrowsExactly(ConfigurationParsingException.class, () -> getMethodSetter(new ParsingContext("foo",
                        null,new GrammarMock().withGlobalElement("b", B.class),null, null,null),
                A2.class, "b"));
    }

    @Test
    void noPotentialSetterFound() {
        assertThrowsExactly(ConfigurationParsingException.class, () -> getMethodSetter(new ParsingContext("foo",
                        null,new GrammarMock().withGlobalElement("c", C.class),null, null,null),
                A2.class, "c"));
    }

    @Test
    void coerceScalar() throws Exception {
        var ms  = new MethodSetter(null, null);
        assertEquals(true, ms.coerceScalarOrReference(null, om.readTree("true"), null, boolean.class));
        assertEquals(true, ms.coerceScalarOrReference(null, om.readTree("true"), null, Boolean.class));
        assertEquals(1, ms.coerceScalarOrReference(null, om.readTree("1"), null, int.class));
        assertEquals(1.0, ms.coerceScalarOrReference(null, om.readTree("1"), null, double.class));
        var l = ms.coerceScalarOrReference(null, om.readTree("1"), null, long.class);
        assertInstanceOf(Long.class, l);
        assertEquals(1L, l);
    }

    @ParameterizedTest
    @MethodSource("directlyConvertedOtherAttributes")
    void otherAttributeValueIsConvertedAccordingToMapValueType(
            Class<?> attributesClass,
            String json,
            Object expectedValue
    ) throws Exception {
        Map<?, ?> attributes = coerceOtherAttribute(attributesClass, json);

        assertEquals(expectedValue, attributes.get(OTHER_ATTRIBUTE_NAME));
    }

    static Stream<Arguments> directlyConvertedOtherAttributes() {
        return Stream.of(
                Arguments.of(IntegerAttributes.class, "5", 5),
                Arguments.of(EnumAttributes.class, "\"FAST\"", Mode.FAST),
                Arguments.of(StringAttributes.class, "\"value\"", "value")
        );
    }

    @Test
    void stringOtherAttributeRejectsNestedObject() {
        assertThrows(ConfigurationParsingException.class,
                () -> coerceOtherAttribute(StringAttributes.class, "{\"nested\":true}"));
    }

    private Map<?, ?> coerceOtherAttribute(Class<?> clazz, String json) throws Exception {
        Method setter = clazz.getMethod("setAttributes", Map.class);
        Object value = new MethodSetter(setter, null)
                .coerceScalarOrReference(null, om.readTree(json), OTHER_ATTRIBUTE_NAME, Map.class);

        return assertInstanceOf(Map.class, value);
    }
}
