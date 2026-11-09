/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.openapi.util.JsonTestUtil.*;
import static java.lang.Boolean.*;
import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;


public class ArrayTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/array.yml";
    }

    @Test
    public void noType() {

        Map<String, Object> m = new HashMap<>();
        m.put("no-type", listWithDifferentTypes());

        ValidationErrors errors = validator.validate(Request.post().path("/array").body(mapToJson(m)));
        assertEquals(0, errors.size());
    }

    @Test
    public void onlyNumbersValid() {

        List<Number> l = new ArrayList<>();
        l.add(7);
        l.add(0.5);
        l.add(10000000);

        Map<String, List<Number>> m = new HashMap<>();
        m.put("only-numbers", l);

        ValidationErrors errors = validator.validate(Request.post().path("/array").body(mapToJson(m)));
        assertEquals(0,errors.size());
    }

    @Test
    public void onlyNumbersInvalid() {

        Map<String, Object> m = new HashMap<>();
        m.put("only-numbers", listWithDifferentTypes());

        ValidationErrors errors = validator.validate(Request.post().path("/array").body(mapToJson(m)));
        assertEquals(2, errors.size());
    }

    @Test
    public void minMaxValid() {

        Map<String, Object> m = new HashMap<>();
        m.put("min-max", Arrays.asList("foo", 7));

        ValidationErrors errors = validator.validate(Request.post().path("/array").body(mapToJson(m)));
        assertEquals(0, errors.size());
    }

    @Test
    public void minMaxTooLessInvalid() {

        Map<String, List<String>> m = new HashMap<>();
        m.put("min-max", singletonList("foo"));

        ValidationErrors errors = validator.validate(Request.post().path("/array").body(mapToJson(m)));
        assertEquals(1, errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/min-max", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("minItems"));
        assertEquals("REQUEST/BODY#/min-max", e.getContext().getLocationForRequest());
    }

    @Test
    public void minMaxTooManyInvalid() {

        Map<String, List<Object>> m = new HashMap<>();
        m.put("min-max", Arrays.asList("foo", 7, true, 8, "bar"));

        ValidationErrors errors = validator.validate(Request.post().path("/array").body(mapToJson(m)));
        assertEquals(1, errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/min-max", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("maxItems"));
        assertEquals("REQUEST/BODY#/min-max", e.getContext().getLocationForRequest());
    }


    @Test
    public void uniqueItemsInvalid() {

        Map<String, List<Integer>> m = new HashMap<>();
        m.put("uniqueItems", Arrays.asList(4, 5, 2, 3, 9, 1, 2, 0));

        ValidationErrors errors = validator.validate(Request.post().path("/array").body(mapToJson(m)));
        assertEquals(1, errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/uniqueItems", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("2"));
        assertEquals("REQUEST/BODY#/uniqueItems", e.getContext().getLocationForRequest());
    }

    @Test
    public void uniqueItemsValid() {

        Map<String, List<Integer>> m = new HashMap<>();
        m.put("uniqueItems", Arrays.asList(4, 5, 2, 3, 9, 1, 0));

        ValidationErrors errors = validator.validate(Request.post().path("/array").body(mapToJson(m)));
        assertEquals(0, errors.size());
    }

    @Test
    public void validateObjectInArrayValid() {

        Map<String, Object> o1 = new HashMap<>();
        o1.put("a", "foo");
        o1.put("b", 7);

        Map<String, Object> o2 = new HashMap<>();
        o2.put("a", "baz");
        o2.put("b", 3);

        Map<String, Object> o3 = new HashMap<>();
        o3.put("a", "bar");
        o3.put("b", 11);

        Map<String, Object> m = new HashMap<>();
        m.put("objects", Arrays.asList(o1, o2, o3));

        ValidationErrors errors = validator.validate(Request.post().path("/array").body(mapToJson(m)));
        assertEquals(0, errors.size());
    }

    @Test
    public void validateObjectInArrayInvalid() {

        Map<String, Object> o1 = new HashMap<>();
        o1.put("a", "foo");
        o1.put("b", 7);

        Map<String, String> o2 = new HashMap<>();
        o2.put("a", "baz");

        Map<String, Object> o3 = new HashMap<>();
        o3.put("a", "bar");
        o3.put("b", 11);

        Map<String, Object> m = new HashMap<>();
        m.put("objects", Arrays.asList(o1, o2, o3));

        ValidationErrors errors = validator.validate(Request.post().path("/array").body(mapToJson(m)));
        assertEquals(1, errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/objects/1/b", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("missing"));
        assertEquals("REQUEST/BODY#/objects/1/b", e.getContext().getLocationForRequest());

    }

    /**
     * In JSON Schema 2020-12 (OAS 3.1 and later) prefixItems validates the items by position and
     * items only the items after the prefix. See #3499.
     */
    @Nested
    class PrefixItems extends AbstractValidatorTest {

        @Override
        protected String getOpenAPIFileName() {
            return "/openapi/specs/oas31/prefix-items.yaml";
        }

        private ValidationErrors post(String path, String body) throws ParseException {
            return validator.validate(Request.post().mediaType(APPLICATION_JSON).path(path).body(body));
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "/closed|[\"a\", 1]",
                "/closed|[\"a\"]",
                "/closed|[]",
                "/open|[\"a\", 1, true, null]",
                "/rest|[\"a\", 1, 2]",
                "/ref|[1.5, 2]"})
        void accepted(String path, String body) throws ParseException {
            final var errors = post(path, body);
            assertEquals(0, errors.size(), errors.toString());
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "/closed|[1, \"a\"]|/0,/1",
                "/closed|[\"a\", 1, true]|/2",
                "/open|[1]|/0",
                "/rest|[\"a\", \"b\"]|/1",
                "/rest|[1, 2]|/0",
                "/ref|[1, 2, 3]|/2"})
        void rejected(String path, String body, String pointers) throws ParseException {
            final var errors = post(path, body);
            assertEquals(List.of(pointers.split(",")),
                    errors.stream().map(e -> e.getContext().getJSONpointer()).toList(), errors.toString());
        }
    }

    private List<Object> listWithDifferentTypes() {
        List<Object> l = new ArrayList<>();
        l.add(7);
        l.add("foo");
        l.add(TRUE);
        return l;
    }
}