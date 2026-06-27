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

import com.predic8.membrane.shaded.io.swagger.v3.oas.models.Components;
import com.predic8.membrane.shaded.io.swagger.v3.oas.models.OpenAPI;
import com.predic8.membrane.shaded.io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

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
}
