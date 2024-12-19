/* Copyright 2024 predic8 GmbH, www.predic8.com
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
   http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exceptions;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

public class ProblemDetailsTest {

    private final static ObjectMapper om = new ObjectMapper();

    @Test
    void simple() throws JsonProcessingException {

        Response r = user(false).addSubType("catastrophy").title("Something happened!").build();

        assertEquals(400, r.getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, r.getHeader().getContentType());

        JsonNode json = om.readTree(r.getBodyAsStringDecoded());

        assertEquals("https://membrane-api.io/error/user/catastrophy",json.get("type").asText());
        assertEquals("Something happened!",json.get("title").asText());
    }

    @Test
    void details() throws JsonProcessingException {
        Response r = user(false)
                .addSubType("catastrophy")
                .title("Something happend!")
                .detail("The barn burned down and the roof fell on cow Elsa.").build();

        JsonNode json = om.readTree(r.getBodyAsStringDecoded());

        assertEquals("The barn burned down and the roof fell on cow Elsa.",json.get("detail").asText());
    }

    @Test
    void extensions() throws JsonProcessingException {
        Response r = user(false)
                .addSubType("catastrophy")
                .title("Something happend!")
                .extension("a","1")
                .extension("b","2").build();

        JsonNode json = om.readTree(r.getBodyAsStringDecoded());

        assertEquals("1",json.get("a").asText());
        assertEquals("2",json.get("b").asText());
    }

    @Test
    void production() throws JsonProcessingException {
        JsonNode json = om.readTree(getResponseWithDetailsAndExtensions(true).getBodyAsStringDecoded());
        assertEquals(3,json.size());
        assertEquals("https://membrane-api.io/error/internal",json.get("type").asText());
        assertEquals("An internal error occurred.",json.get("title").asText());
        assertTrue(json.get("detail").asText().contains("can be found in the Membrane log"));
    }

    @Test
    void noProduction() throws JsonProcessingException {
        JsonNode json = om.readTree(getResponseWithDetailsAndExtensions(false).getBodyAsStringDecoded());
        assertEquals(6,json.size());
        assertEquals("https://membrane-api.io/error/user/catastrophy",json.get("type").asText());
        assertEquals("Something happend!",json.get("title").asText());
        assertEquals("A detailed description.",json.get("detail").asText());
    }

    private static Response getResponseWithDetailsAndExtensions(boolean production) {
        return user(production)
                .addSubType("catastrophy")
                .title("Something happend!")
                .detail("A detailed description.")
                .extension("a","1")
                .extension("b","2").build();
    }

    @Test
    void exception() throws JsonProcessingException {
        Response r = internal(true).addSubType("catastrophy").title("Something happend!")
                .detail("A detailed description.")
                .extension("a","1")
                .extension("b","2").build();

        JsonNode json = om.readTree(r.getBodyAsStringDecoded());

        assertEquals(3,json.size());
        assertEquals("https://membrane-api.io/error/internal",json.get("type").asText());
        assertEquals("An internal error occurred.",json.get("title").asText());
        assertTrue(json.get("detail").asText().contains("can be found in the Membrane log"));
    }

    @Test
    void parse() throws JsonProcessingException {
        Response r =  ProblemDetails.user(false)
                .addSubType("validation")
                .statusCode(421)
                .title("Validation error")
                .instance("server-1")
                .detail("Wrong format")
                .build();

        ProblemDetails pd = ProblemDetails.parse(r);

        assertEquals(421,pd.getStatusCode());
        assertEquals("https://membrane-api.io/error/user/validation",pd.getType());
    }
}