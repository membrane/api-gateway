/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.util;

import com.predic8.membrane.core.openapi.OpenAPIParsingException;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class SchemaUtilTest {

    private static OpenAPI apiWithSchema(String name, Schema schema) {
        return new OpenAPI().components(new Components().addSchemas(name, schema));
    }

    // -----------------------------------------------------------------------
    // isObjectOrArray must work for OAS 3.0 (type) and OAS 3.1 (types array)
    // -----------------------------------------------------------------------

    @Test
    void objectAndArrayDetectedForOAS30Type() {
        assertTrue(SchemaUtil.isObjectOrArray(new OpenAPI(), new Schema().type("object")));
        assertTrue(SchemaUtil.isObjectOrArray(new OpenAPI(), new Schema().type("array")));
    }

    @Test
    void objectAndArrayDetectedForOAS31TypesArray() {
        assertTrue(SchemaUtil.isObjectOrArray(new OpenAPI(), new Schema().typesItem("object")));
        assertTrue(SchemaUtil.isObjectOrArray(new OpenAPI(), new Schema().typesItem("array")));
    }

    @Test
    void nullableObjectDetectedForOAS31TypesArray() {
        assertTrue(SchemaUtil.isObjectOrArray(new OpenAPI(), new Schema().typesItem("object").typesItem("null")));
    }

    @Test
    void refToObjectIsObjectOrArray() {
        OpenAPI api = apiWithSchema("Foo", new Schema().type("object"));
        assertTrue(SchemaUtil.isObjectOrArray(api, new Schema().$ref("#/components/schemas/Foo")));
    }

    @Test
    void refToScalarIsNotObjectOrArray() {
        OpenAPI api = apiWithSchema("Foo", new Schema().type("string"));
        assertFalse(SchemaUtil.isObjectOrArray(api, new Schema().$ref("#/components/schemas/Foo")));
    }

    @Test
    void unresolvableRefIsNotObjectOrArray() {
        assertFalse(SchemaUtil.isObjectOrArray(apiWithSchema("Bar", new Schema().type("object")),
                new Schema().$ref("#/components/schemas/Foo")));
    }

    @Test
    void schemaWithPropertiesButNoTypeIsObjectOrArray() {
        assertTrue(SchemaUtil.isObjectOrArray(new OpenAPI(), new Schema().addProperty("a", new Schema().type("string"))));
    }

    @Test
    void scalarsAreNotObjectOrArray() {
        assertFalse(SchemaUtil.isObjectOrArray(new OpenAPI(), new Schema().type("string")));
        assertFalse(SchemaUtil.isObjectOrArray(new OpenAPI(), new Schema().type("integer")));
        assertFalse(SchemaUtil.isObjectOrArray(new OpenAPI(), new Schema().typesItem("string")));
        assertFalse(SchemaUtil.isObjectOrArray(new OpenAPI(), new Schema()));
    }

    // -----------------------------------------------------------------------
    // getEffectiveType underpins the above
    // -----------------------------------------------------------------------

    @Test
    void getEffectiveTypePrefersTypeThenTypes() {
        assertEquals("object", SchemaUtil.getEffectiveType(new Schema().type("object")));
        assertEquals("array", SchemaUtil.getEffectiveType(new Schema().typesItem("array")));
        assertEquals("string", SchemaUtil.getEffectiveType(new Schema().typesItem("string").typesItem("null")));
        assertNull(SchemaUtil.getEffectiveType(new Schema()));
    }

    @Test
    void itemSchemaPrefersPrefixItemsThenItems() {
        final var first = new Schema().type("integer");
        final var items = new Schema().type("string");
        final var schema = new Schema().type("array").prefixItems(List.of(first)).items(items);

        assertSame(first, SchemaUtil.itemSchema(schema, 0));
        assertSame(items, SchemaUtil.itemSchema(schema, 1));
        assertNull(SchemaUtil.itemSchema(new Schema().type("array").prefixItems(List.of(first)), 1));
        assertSame(items, SchemaUtil.itemSchema(new Schema().type("array").items(items), 0));
    }

    @Test
    void patternPropertyPatternsAreCompiledOnceAndShared() {
        final var schema = new Schema().addPatternProperty("^x-", new Schema());
        final var patterns = SchemaUtil.getPatternPropertyPatterns(schema);
        assertEquals(1, patterns.size());
        assertTrue(patterns.getFirst().matcher("x-trace").find());
        assertSame(patterns.getFirst(), SchemaUtil.getPatternPropertyPatterns(schema).getFirst());
    }

    @Test
    void noPatternPropertiesGivesNoPatterns() {
        assertTrue(SchemaUtil.getPatternPropertyPatterns(new Schema()).isEmpty());
    }

    @Test
    void invalidPatternPropertyRegexIsSpecError() {
        final var e = assertThrows(OpenAPIParsingException.class, () -> SchemaUtil.compilePatternPropertyRegex("["));
        assertTrue(e.getMessage().contains("'['"), e::getMessage);
        assertInstanceOf(PatternSyntaxException.class, e.getCause());
    }

    // -----------------------------------------------------------------------
    // parseScalar parses numbers exactly, up to MAX_NUMBER_LENGTH characters
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"5.0000000000000000001", "1e400", "-0.1", " 42 "})
    void numberIsParsedExactly(String text) {
        final var node = SchemaUtil.parseScalar(text, new Schema<>().type("number"));
        assertTrue(node.isNumber(), node::toString);
        assertEquals(0, new BigDecimal(text.trim()).compareTo(node.decimalValue()), node::toString);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1d", "1f", "0x1p3", "NaN", "Infinity", "abc"})
    void nonJsonNumberIsKeptAsText(String text) {
        assertTrue(SchemaUtil.parseScalar(text, new Schema<>().type("number")).isTextual());
    }

    @Test
    void integerBeyondLongIsParsedExactly() {
        final var node = SchemaUtil.parseScalar("123456789012345678901234567890", new Schema<>().type("integer"));
        assertTrue(node.isIntegralNumber(), node::toString);
        assertEquals(new BigInteger("123456789012345678901234567890"), node.bigIntegerValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"number", "integer"})
    void numberUpToMaxLengthIsParsed(String type) {
        final var digits = "1".repeat(SchemaUtil.MAX_NUMBER_LENGTH);
        final var node = SchemaUtil.parseScalar(digits, new Schema<>().type(type));
        assertTrue(node.isNumber(), node::toString);
        assertEquals(0, new BigDecimal(digits).compareTo(node.decimalValue()));
    }

    /**
     * Parsing takes quadratic time in the number of digits, about 10 s for a million digits.
     */
    @ParameterizedTest
    @ValueSource(strings = {"number", "integer"})
    void longerNumberIsKeptAsTextWithoutParsing(String type) {
        assertTrue(SchemaUtil.parseScalar("1".repeat(SchemaUtil.MAX_NUMBER_LENGTH + 1), new Schema<>().type(type)).isTextual());
        final var digits = "1".repeat(1_000_000);
        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> assertTrue(SchemaUtil.parseScalar(digits, new Schema<>().type(type)).isTextual()));
    }
}
