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

package com.predic8.membrane.annot.yaml.parsing.binding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCOtherAttributes;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Stream;

import static com.predic8.membrane.annot.yaml.parsing.binding.ScalarValueConverterTest.Mode.FAST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScalarValueConverterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OTHER_ATTRIBUTE_NAME = "timeout";

    private final ScalarValueConverter converter = new ScalarValueConverter();

    @SuppressWarnings("unused")
    static class IntegerAttributes {
        @MCOtherAttributes
        public void setAttributes(Map<String, Integer> attributes) {
        }
    }

    @SuppressWarnings("unused")
    static class EnumAttributes {
        @MCOtherAttributes
        public void setAttributes(Map<String, Mode> attributes) {
        }
    }

    @SuppressWarnings("unused")
    static class StringAttributes {
        @MCOtherAttributes
        public void setAttributes(Map<String, String> attributes) {
        }
    }

    enum Mode {
        FAST
    }

    @Test
    void coercesNonTextualScalars() throws Exception {
        assertEquals(true, coerce("true", boolean.class));
        assertEquals(true, coerce("true", Boolean.class));
        assertEquals(1, coerce("1", int.class));
        assertEquals(1.0, coerce("1", double.class));

        Object value = coerce("1", long.class);

        assertInstanceOf(Long.class, value);
        assertEquals(1L, value);
    }

    @ParameterizedTest
    @MethodSource("directlyConvertedOtherAttributes")
    void otherAttributeValueIsConvertedAccordingToMapValueType(
            Class<?> attributesClass,
            String json,
            Object expectedValue
    ) throws Exception {
        assertEquals(expectedValue, coerceOtherAttribute(attributesClass, json).get(OTHER_ATTRIBUTE_NAME));
    }

    static Stream<Arguments> directlyConvertedOtherAttributes() {
        return Stream.of(
                Arguments.of(IntegerAttributes.class, "5", 5),
                Arguments.of(EnumAttributes.class, "\"FAST\"", FAST),
                Arguments.of(StringAttributes.class, "\"value\"", "value")
        );
    }

    @Test
    void stringOtherAttributeRejectsNestedObject() {
        assertThrows(ConfigurationParsingException.class,
                () -> coerceOtherAttribute(StringAttributes.class, "{\"nested\":true}"));
    }

    private Object coerce(String json, Class<?> wanted) throws Exception {
        return converter.coerceScalarOrReference(null, null, MAPPER.readTree(json), "value", wanted);
    }

    private Map<?, ?> coerceOtherAttribute(Class<?> clazz, String json) throws Exception {
        Method setter = clazz.getMethod("setAttributes", Map.class);
        Object value = converter.coerceScalarOrReference(null, setter, MAPPER.readTree(json), OTHER_ATTRIBUTE_NAME, Map.class);

        return assertInstanceOf(Map.class, value);
    }
}
