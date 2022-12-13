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

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.util.JsonUtil.mapToJson;
import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@SuppressWarnings("OptionalGetWithoutIsPresent")
public class CompositionTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this, "/openapi/specs/composition.yml"));
    }

    @Test
    public void allOfValid() {

        Map<String,String> m = new HashMap<>();
        m.put("firstname","Otto");

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void allOfTooLongInvalid() {

        Map<String,String> m = new HashMap<>();
        m.put("firstname","123456");

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(2,errors.size());

        ValidationError enumError = errors.stream().filter(e -> e.getMessage().contains("axLength")).findAny().get();
        assertEquals("/firstname", enumError.getContext().getJSONpointer());
        assertEquals("REQUEST/BODY#/firstname", enumError.getContext().getLocationForRequest());

        ValidationError allOf = errors.stream().filter(e -> e.getMessage().contains("allOf")).findAny().get();
        assertEquals("/firstname", allOf.getContext().getJSONpointer());
        assertTrue(allOf.getMessage().contains("subschemas"));
        assertEquals("REQUEST/BODY#/firstname", allOf.getContext().getLocationForRequest());

    }

    @Test
    public void allOfTooShortInvalid() {

        Map<String,String> m = new HashMap<>();
        m.put("firstname","12");

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(2,errors.size());

        ValidationError enumError = errors.stream().filter(e -> e.getMessage().contains("minLength")).findAny().get();
        assertEquals("/firstname", enumError.getContext().getJSONpointer());

        ValidationError allOf = errors.stream().filter(e -> e.getMessage().contains("allOf")).findAny().get();
        assertEquals("/firstname", allOf.getContext().getJSONpointer());
        assertTrue(allOf.getMessage().contains("subschemas"));
    }

    @Test
    public void anyOfeMailValid() {

        Map<String,String> m = new HashMap<>();
        m.put("contact","membrane@predic8.de");

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void anyOfTelValid() {

        Map<String,String> m = new HashMap<>();
        m.put("contact","123");

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void anyOfInvalid() {

        Map<String,String> m = new HashMap<>();
        m.put("contact","Bonn");

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/contact", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("anyOf"));
    }

    @Test
    public void oneOfNoneInvalid() {

        Map<String,Object> m = new HashMap<>();
        m.put("multiple",7);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/multiple", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("neOf"));
    }

    @Test
    public void oneOfTwoInvalid() {

        Map<String,Object> m = new HashMap<>();
        m.put("multiple",15);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/multiple", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("neOf"));
    }

    @Test
    public void oneOfValid() {

        Map<String,Integer> m = new HashMap<>();
        m.put("multiple",21);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void factoredOutInvalid() {

        Map<String,Integer> m = new HashMap<>();
        m.put("factored-out",15);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/factored-out", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("neOf"));
        assertTrue(e.getMessage().contains("2 subschemas"));
    }

    @Test
    public void factoredOutValid() {

        Map<String,Integer> m = new HashMap<>();
        m.put("factored-out",21);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void notStringValid() {

        Map<String,Boolean> m = new HashMap<>();
        m.put("not-string",true);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void notStringInvalid() {

        Map<String,String> m = new HashMap<>();
        m.put("not-string","abc");

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/not-string", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("not"));
    }

    @Test
    public void inheritanceValid() {

        Map<String,String> address = new HashMap<>();
        address.put("street","Koblenzer Str. 65");
        address.put("city","Bonn");
        address.put("country","IN"); // DE is not valid

        Map<String,Map<String,String>> inheritance = new HashMap<>();
        inheritance.put("inheritance",address);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(inheritance)));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void inheritanceWrongPlaceInvalid() {

        Map<String,String> address = new HashMap<>();
        address.put("street","Koblenzer Str. 65");
        address.put("city","Bonn");
        address.put("country","DE"); // DE is not valid

        Map<String,Map<String,String>> inheritance = new HashMap<>();
        inheritance.put("inheritance",address);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(inheritance)));
//        System.out.println("errors = " + errors);
        assertEquals(2,errors.size());
        ValidationError enumError = errors.stream().filter(e -> e.getMessage().contains("enum")).findAny().get();
        assertEquals("/inheritance/country", enumError.getContext().getJSONpointer());
        assertTrue(enumError.getMessage().contains("does not contain a value from the enum"));
        assertEquals("REQUEST/BODY#/inheritance/country", enumError.getContext().getLocationForRequest());

        ValidationError allOf = errors.stream().filter(e -> e.getMessage().contains("allOf")).findAny().get();
        assertEquals("/inheritance", allOf.getContext().getJSONpointer());
        assertTrue(allOf.getMessage().contains("subschemas of allOf"));
        assertEquals("REQUEST/BODY#/inheritance", allOf.getContext().getLocationForRequest());

    }

    @Test
    public void inheritanceInvalid() {

        Map<String,String> address = new HashMap<>();
        address.put("street","Koblenzer Str. 65");
        address.put("city","Bonn");

        Map<String,Map<String,String>> inheritance = new HashMap<>();
        inheritance.put("inheritance",address);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(inheritance)));
//        System.out.println("errors = " + errors);
        assertEquals(2,errors.size());

        ValidationError enumError = errors.stream().filter(e -> e.getMessage().toLowerCase().contains("required")).findAny().get();
        assertEquals("/inheritance/country", enumError.getContext().getJSONpointer());

        ValidationError allOf = errors.stream().filter(e -> e.getMessage().contains("allOf")).findAny().get();
        assertEquals("/inheritance", allOf.getContext().getJSONpointer());
        assertTrue(allOf.getMessage().contains("subschemas"));

    }
}