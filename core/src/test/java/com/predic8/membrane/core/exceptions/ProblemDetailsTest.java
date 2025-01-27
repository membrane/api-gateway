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
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import org.xml.sax.*;


import javax.xml.xpath.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class ProblemDetailsTest {

    private static final XPathFactory xPathFactory = XPathFactory.newInstance();
    private final static ObjectMapper om = new ObjectMapper();

    @Test
    void simple() throws JsonProcessingException {

        Response r = user(false,"component-a")
                .addSubType("catastrophy")
                .title("Something happened!")
                .build();

        assertEquals(400, r.getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, r.getHeader().getContentType());

//        System.out.println("r.getBodyAsStringDecoded() = " + r.getBodyAsStringDecoded());

        JsonNode json = om.readTree(r.getBodyAsStringDecoded());

        assertEquals("Something happened!",json.get("title").asText());
        assertEquals("https://membrane-api.io/problems/user/catastrophy",json.get("type").asText());

        // Assert Order
        assertIterableEquals(List.of("title","type","see","attention"), CollectionsUtil.toList( json.fieldNames()));
    }

    @Test
    void details() throws JsonProcessingException {
        Response r = user(false, "component-b")
                .addSubType("catastrophy")
                .title("Something happend!")
                .detail("The barn burned down and the roof fell on cow Elsa.").build();

//        System.out.println("r.getBodyAsStringDecoded() = " + r.getBodyAsStringDecoded());

        JsonNode json = om.readTree(r.getBodyAsStringDecoded());

        assertEquals("The barn burned down and the roof fell on cow Elsa.", json.get("detail").asText());
    }

    @Test
    void extensions() throws JsonProcessingException {
        Response r = user(false, "component c")
                .addSubType("catastrophy")
                .title("Something happend!")
                .internal("a", "1")
                .internal("b", "2").build();

//        System.out.println("r.getBodyAsStringDecoded() = " + r.getBodyAsStringDecoded());

        JsonNode json = om.readTree(r.getBodyAsStringDecoded());

        assertEquals("1", json.get("a").asText());
        assertEquals("2", json.get("b").asText());
    }

    @Test
    void productionUser() throws JsonProcessingException {
        JsonNode json = om.readTree(getResponseWithDetailsAndExtensions(true).getBodyAsStringDecoded());
        assertEquals(3,json.size());
        assertEquals("https://membrane-api.io/problems/user/catastrophy",json.get("type").asText());
        assertEquals("Something happend!", json.get("title").asText());
    }

    @Test
    void noProduction() throws JsonProcessingException {
        String pdJson = getResponseWithDetailsAndExtensions(false).getBodyAsStringDecoded();
//        System.out.println("pdJson = " + pdJson);
        JsonNode json = om.readTree(pdJson);
        assertEquals(7,json.size());
        assertEquals("https://membrane-api.io/problems/user/catastrophy",json.get("type").asText());
        assertEquals("Something happend!",json.get("title").asText());
        assertEquals("A detailed description.",json.get("detail").asText());
    }

    private static Response getResponseWithDetailsAndExtensions(boolean production) {
        return user(production, "component a b c")
                .addSubType("catastrophy")
                .title("Something happend!")
                .detail("A detailed description.")
                .internal("a", "1")
                .internal("b", "2").build();
    }

    @Test
    void exception() throws JsonProcessingException {
        Response r = internal(true, "a b").addSubType("catastrophe").title("Something happened!")
                .detail("A detailed description.")
                .internal("a", "1")
                .internal("b", "2").build();

//        System.out.println("r.getBodyAsStringDecoded() = " + r.getBodyAsStringDecoded());
        
        JsonNode json = om.readTree(r.getBodyAsStringDecoded());

        assertEquals(3,json.size());
        assertEquals("https://membrane-api.io/problems/internal/catastrophe",json.get("type").asText());
        assertEquals("Something happened!",json.get("title").asText());
    }

    @Test
    void parse() throws JsonProcessingException {
        ProblemDetails pd = ProblemDetails.parse(ProblemDetails.user(false, "a")
                .addSubType("validation")
                .statusCode(421)
                .title("Validation error")
                .detail("Wrong format")
                .build());

        assertEquals(421,pd.getStatusCode());
        assertEquals("https://membrane-api.io/problems/user/validation",pd.getType());
    }

    @Test
    void see() throws JsonProcessingException {
        Response r = user(false,"component-b")
                .title("Something happend!")
                .flow(REQUEST)
                .component("flux-generator")
                .addSubSee("io")
                .build();

//        System.out.println("r.getBodyAsStringDecoded() = " + r.getBodyAsStringDecoded());

        JsonNode json = om.readTree(r.getBodyAsStringDecoded());

        assertEquals("https://membrane-api.io/problems/user/flux-generator/request/io",json.get("see").asText());
    }

    @Test
    void causeStacktrace() throws IOException {
        byte[] responseBody = internal(false, "a")
                .exception(new RuntimeException("b", new InnerExceptionGenerator().generate()))
                .build().getBody().getContentAsStream().readAllBytes();

        String b = new String(responseBody, UTF_8);
//        System.out.println(b);

        assertTrue(b.contains("InnerExceptionGenerator"));
        assertTrue(b.contains("more_frames_in_common"));
    }

    @Test
    void xmlPd() throws Exception {
        Exchange exc = Request.post("/foo")
                .contentType(APPLICATION_XML)
                .buildExchange();

        user(false, "blaster")
                .addSubType("atomic")
                .title("Catastrophe!")
                .internal("foo", "7")
                .buildAndSetResponse(exc);

        String body = exc.getResponse().getBodyAsStringDecoded();
        assertTrue(exc.getResponse().isXML());

        assertEquals("Catastrophe!", xPath(body, "/problem-details/title"));
        assertEquals("https://membrane-api.io/problems/user/atomic", xPath(body, "/problem-details/type"));
        assertEquals("7", xPath(body, "/problem-details/foo"));
        assertTrue(xPath(body, "/problem-details/attention").contains("development mode"));
    }

    private static String xPath(String body, String expression) throws XPathExpressionException {
        return xPathFactory.newXPath().evaluate(expression, new InputSource(new StringReader(body)));
    }

    private static class InnerExceptionGenerator {
        public Exception generate() {
            return new RuntimeException("inner");
        }
    }
}