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
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static org.junit.jupiter.api.Assertions.*;


public class RequiredTest extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/required.yml";
    }


    @Test
    public void normalValid() {

        Map<String,Integer> props = new HashMap<>();
        props.put("a",5);
        props.put("b",3);
        props.put("c",6);

        Map<String,Map<String,Integer>> o = new HashMap<>();
        o.put("normal", props);

        ValidationErrors errors = validator.validate(Request.post().path("/required").body(mapToJson(o)));
        assertEquals(0,errors.size());
    }

    @Test
    public void normalMissingRequiredInvalid() {

        Map<String,Integer> props = new HashMap<>();
        props.put("a",5);
        props.put("c",6);

        Map<String,Map<String,Integer>> o = new HashMap<>();
        o.put("normal", props);

        ValidationErrors errors = validator.validate(Request.post().path("/required").body(mapToJson(o)));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/normal/b", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("Required"));
        assertEquals("REQUEST/BODY#/normal/b", e.getContext().getLocationForRequest());
    }

    @Test
    public void normalMissingMoreRequiredInvalid() {

        Map<String,Integer> props = new HashMap<>();
        props.put("c",6);

        Map<String,Map<String,Integer>> o = new HashMap<>();
        o.put("normal", props);

        ValidationErrors errors = validator.validate(Request.post().path("/required").body(mapToJson(o)));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/normal", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("Required"));
        assertTrue(e.getMessage().contains("a,b"));
    }

    @Test
    public void requestRequiredReadOnlyValid() {

        Map<String,Integer> props = new HashMap<>();
        props.put("b",3);
        props.put("c",6);

        Map<String,Map<String,Integer>> readOnlyRequest = new HashMap<>();
        readOnlyRequest.put("read-only-request", props);

        ValidationErrors errors = validator.validate(Request.post().path("/required").body(mapToJson(readOnlyRequest)));
        assertEquals(0,errors.size());
    }

    @Test
    public void responseRequiredWriteOnlyValid() throws ParseException {

        Map<String,Integer> props = new HashMap<>();
        props.put("b",3);
        props.put("c",6);

        Map<String,Map<String,Integer>> writeOnlyResponse = new HashMap<>();
        writeOnlyResponse.put("write-only-response", props);

        ValidationErrors errors = validator.validateResponse(Request.get().path("/required"), Response.statusCode(200).mediaType(APPLICATION_JSON).body(mapToJson(writeOnlyResponse)));
        assertEquals(0,errors.size());
    }
}