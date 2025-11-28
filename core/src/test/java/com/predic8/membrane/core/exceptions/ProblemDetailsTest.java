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

import tools.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;
import org.xml.sax.*;

import javax.xml.xpath.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.util.CollectionsUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class ProblemDetailsTest {

    private static final XPathFactory xPathFactory = XPathFactory.newInstance();
    private static final ObjectMapper om = new ObjectMapper();

    @Nested
    class productionFalse {

        @Test
        void simple() throws Exception {

            Response r = user(false, "component-a")
                    .addSubType("catastrophe")
                    .title("Something happened!")
                    .build();

            assertEquals(400, r.getStatusCode());
            assertEquals(APPLICATION_PROBLEM_JSON, r.getHeader().getContentType());

            JsonNode json = parseJson(r);

            assertEquals("Something happened!", json.get(TITLE).asText());
            assertEquals("https://membrane-api.io/problems/user/catastrophe", json.get(TYPE).asText());

            assertTrue(toList(json.fieldNames()).containsAll(List.of(TITLE, TYPE, STATUS, SEE, ATTENTION)));
        }

        @Test
        void internals() throws Exception {

            Response r = user(false, "a")
                    .addSubType("catastrophe")
                    .title("Something happened!")
                    .internal("foo", "baz")
                    .build();

            JsonNode j = parseJson(r);

            assertEquals("baz", j.get("foo").asText());
            assertTrue(j.hasNonNull(ATTENTION));
        }

        @Test
        void details() throws Exception {
            Response r = user(false, "component-b")
                    .addSubType("catastrophe")
                    .title("Something happened!")
                    .detail("The barn burned down and the roof fell on cow Elsa.").build();

            JsonNode json = parseJson(r);

            assertEquals("The barn burned down and the roof fell on cow Elsa.", json.get(DETAIL).asText());
        }

        @Test
        void extensions() throws Exception {
            Response r = user(false, "component c")
                    .addSubType("catastrophe")
                    .title("Something happened!")
                    .internal("a", "1")
                    .internal("b", "2").build();

            JsonNode json = parseJson(r);

            assertEquals("1", json.get("a").asText());
            assertEquals("2", json.get("b").asText());
        }

        @Test
        void nonProduction() throws Exception {
            JsonNode j = parseJson(getResponseWithDetailsAndExtensions(false));
            assertTrue(j.hasNonNull(TITLE));
            assertTrue(j.hasNonNull(TYPE));
            assertTrue(j.hasNonNull(STATUS));
            assertTrue(j.hasNonNull(DETAIL));
            assertTrue(j.hasNonNull("a"));
            assertTrue(j.hasNonNull("b"));
            assertTrue(j.hasNonNull(SEE));
            assertTrue(j.hasNonNull(ATTENTION));
            assertEquals("https://membrane-api.io/problems/user/catastrophe", j.get(TYPE).asText());
            assertEquals("Something happened!", j.get(TITLE).asText());
            assertEquals("A detailed description.", j.get(DETAIL).asText());
        }

        @Test
        void see() throws Exception {
            Response r = user(false, "component-b")
                    .title("Something happened!")
                    .flow(REQUEST)
                    .component("flux-generator")
                    .addSubSee("io")
                    .build();

            JsonNode json = parseJson(r);

            assertEquals("https://membrane-api.io/problems/user/flux-generator/request/io", json.get(SEE).asText());
        }

        @Test
        void causeStacktrace() {
            String b = internal(false, "a")
                    .exception(new RuntimeException("b", new InnerExceptionGenerator().generate()))
                    .build().getBodyAsStringDecoded();

            assertTrue(b.contains("InnerExceptionGenerator"));
            assertTrue(b.contains("more_frames_in_common"));
        }

        @Test
        void exceptionStacktrace() throws Exception {

            Response r = user(false, "a")
                    .title("Something happened!")
                    .exception(new Exception("And the message is..."))
                    .stacktrace(true)
                    .build();

            JsonNode j = parseJson(r);
            assertEquals("And the message is...", j.get(MESSAGE).asText());
            assertTrue(j.hasNonNull(STACK_TRACE));
        }

        @Test
        void exceptionButNoStacktrace() throws Exception {

            Response r = user(false, "a")
                    .title("Something happened!")
                    .exception(new Exception("And the message is..."))
                    .stacktrace(false)
                    .build();

            JsonNode j = parseJson(r);
            assertEquals("And the message is...", j.get(MESSAGE).asText());
            assertFalse(j.hasNonNull(STACK_TRACE));
        }
    }

    @Nested
    class production {

        @Test
        void userDetailsException() throws Exception {
            JsonNode json = parseJson(getResponseWithDetailsAndExtensions(true));
            assertEquals(4, json.size());
            assertTrue(toList(json.fieldNames()).containsAll(List.of(TITLE, TYPE, STATUS, DETAIL)));
            assertEquals("https://membrane-api.io/problems/user/catastrophe", json.get(TYPE).asText());
            assertEquals("Something happened!", json.get(TITLE).asText());
        }

        @Test
        void hidesInternal() throws Exception {
            Response r = internal(true, "a b").addSubType("catastrophe")
                    .title("Something happened!")
                    .detail("A detailed description.")
                    .internal("a", "1")
                    .internal("b", "2").build();

            JsonNode j = parseJson(r);

            assertEquals(4, j.size());
            assertFalse(j.has("a"));
            assertFalse(j.has("b"));
            assertTrue(toList(j.fieldNames()).containsAll(List.of(TITLE, TYPE, STATUS, DETAIL)));
            assertEquals("https://membrane-api.io/problems/internal", j.get(TYPE).asText());
            assertEquals(INTERNAL_SERVER_ERROR, j.get(TITLE).asText());
        }

        @Nested
        class status400 {

            @Test
            void productionExceptionNoStacktraceStillHasDetail() throws Exception {
                Response r = user(true, "x")
                        .title("Hidden")
                        .exception(new Exception("boom"))
                        .stacktrace(false)
                        .build();
                JsonNode j = parseJson(r);
                assertEquals(400, j.get(STATUS).asInt());
                assertTrue(j.hasNonNull(DETAIL));
                assertTrue(j.get(DETAIL).asText().contains("key")); // references log key
                assertFalse(j.has(ATTENTION));
            }

            @Test
            void internals() throws Exception {

                Response r = user(true, "a")
                        .addSubType("catastrophe")
                        .title("Something happened!")
                        .internal("foo", "baz")
                        .build();

                JsonNode j = parseJson(r);

                assertFalse(j.hasNonNull("foo"));
                assertTrue(j.hasNonNull(DETAIL));
                assertTrue(j.get(DETAIL).asText().contains("key"));
                assertFalse(j.has(ATTENTION));
            }

            @Test
            void subType() throws Exception {
                Response r = user(true, "a")
                        .title("Validation failed!")
                        .addSubType("validation")
                        .build();

                JsonNode j = parseJson(r);
                assertEquals("https://membrane-api.io/problems/user/validation", j.get(TYPE).asText());
            }
        }
    }

    @Nested
    class xml {
        @Test
        void pd() throws Exception {
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
            assertEquals(APPLICATION_PROBLEM_XML, exc.getResponse().getHeader().getContentType());

            assertEquals("Catastrophe!", xPath(body, "/problem-details/title"));
            assertEquals("https://membrane-api.io/problems/user/atomic", xPath(body, "/problem-details/type"));
            assertEquals("7", xPath(body, "/problem-details/foo"));
            assertTrue(xPath(body, "/problem-details/attention").contains("development mode"));
        }
    }

    private static Response getResponseWithDetailsAndExtensions(boolean production) {
        return user(production, "component a b c")
                .addSubType("catastrophe")
                .title("Something happened!")
                .detail("A detailed description.")
                .internal("a", "1")
                .internal("b", "2").build();
    }

    private static String xPath(String body, String expression) throws XPathExpressionException {
        return xPathFactory.newXPath().evaluate(expression, new InputSource(new StringReader(body)));
    }

    private static class InnerExceptionGenerator {
        public Exception generate() {
            return new RuntimeException("inner");
        }
    }

    private static JsonNode parseJson(Response r) throws Exception {
        return om.readTree(r.getBodyAsStringDecoded());
    }
}