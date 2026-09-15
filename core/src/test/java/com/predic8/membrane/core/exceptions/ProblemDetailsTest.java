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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.EOFException;
import java.net.URISyntaxException;
import java.io.StringReader;
import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.ACCEPT;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.util.CollectionsUtil.toList;
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

    @Nested
    class html {

        private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,*/*;q=0.8";

        @Test
        @DisplayName("A browser asking for a path no API is deployed on gets an HTML page")
        void notFound() throws Exception {
            Exchange exc = get("/shop/v2").buildExchange();

            user(false, "openapi")
                    .title("No matching API found!")
                    .status(404)
                    .detail("There is no API on the path /shop/v2 deployed.")
                    .topLevel("path", "/shop/v2")
                    .buildAndSetResponse(exc);

            assertEquals(404, exc.getResponse().getStatusCode());
            assertEquals(TEXT_HTML_UTF8, exc.getResponse().getHeader().getContentType());

            String body = exc.getResponse().getBodyAsStringDecoded();
            assertTrue(body.startsWith("<!DOCTYPE html>"), body);
            assertTrue(body.contains("<title>404 - Not Found</title>"), body);
            assertTrue(body.contains(">404<"), body);
            assertTrue(body.contains("No matching API found!"), body);
            assertTrue(body.contains("There is no API on the path /shop/v2 deployed."), body);
            assertTrue(body.contains("https://www.membrane-api.io"), body);
        }

        @Test
        @DisplayName("Fields beyond the headline are listed, nested ones included")
        void fieldsAreListed() throws Exception {
            Exchange exc = get("/foo").buildExchange();

            user(false, "blaster")
                    .addSubType("atomic")
                    .title("Catastrophe!")
                    .internal("foo", "7")
                    .buildAndSetResponse(exc);

            String body = exc.getResponse().getBodyAsStringDecoded();
            assertTrue(body.contains("https://membrane-api.io/problems/user/atomic"), body);
            assertTrue(body.contains("<dt>foo</dt>"), body);
            assertTrue(body.contains("<dd>7</dd>"), body);
        }

        @Test
        @DisplayName("Everything from the request is escaped, so a crafted path cannot inject markup")
        void escapesValues() throws Exception {
            Exchange exc = get("/foo").buildExchange();

            user(false, "openapi")
                    .status(404)
                    .detail("There is no API on the path <script>alert(1)</script> deployed.")
                    .topLevel("path", "<img src=x onerror=alert(1)>")
                    .buildAndSetResponse(exc);

            String body = exc.getResponse().getBodyAsStringDecoded();
            assertFalse(body.contains("<script>"), body);
            assertFalse(body.contains("<img src=x"), body);
            assertTrue(body.contains("&lt;script&gt;"), body);
        }

        @Test
        @DisplayName("Production mode withholds the details from the page just as it does from JSON")
        void production() throws Exception {
            Exchange exc = get("/foo").buildExchange();

            user(true, "openapi")
                    .status(404)
                    .title("No matching API found!")
                    .detail("There is no API on the path /shop/v2 deployed.")
                    .internal("stage", "production")
                    .buildAndSetResponse(exc);

            String body = exc.getResponse().getBodyAsStringDecoded();
            assertFalse(body.contains("/shop/v2"), body);
            assertFalse(body.contains("production"), body);
            assertTrue(body.contains("Internal details are hidden."), body);
        }

        @Test
        @DisplayName("A client that only tolerates HTML behind its real preference still gets JSON")
        void htmlWithLowerQualityDoesNotWin() throws Exception {
            Exchange exc = Request.get("/foo")
                    .header(ACCEPT, "application/json, text/html;q=0.1")
                    .buildExchange();

            user(false, "openapi").status(404).buildAndSetResponse(exc);

            assertEquals(APPLICATION_PROBLEM_JSON, exc.getResponse().getHeader().getContentType());
        }

        @Test
        @DisplayName("A wildcard leaves the choice to the gateway, which stays machine readable")
        void wildcardStaysJson() throws Exception {
            Exchange exc = Request.get("/foo").header(ACCEPT, "*/*").buildExchange();

            user(false, "openapi").status(404).buildAndSetResponse(exc);

            assertEquals(APPLICATION_PROBLEM_JSON, exc.getResponse().getHeader().getContentType());
        }

        @Test
        @DisplayName("A malformed Accept header falls back to JSON instead of failing")
        void malformedAccept() throws Exception {
            Exchange exc = Request.get("/foo").header(ACCEPT, "text/html;q=").buildExchange();

            user(false, "openapi").status(404).buildAndSetResponse(exc);

            assertEquals(APPLICATION_PROBLEM_JSON, exc.getResponse().getHeader().getContentType());
        }

        private static Request.Builder get(String path) throws URISyntaxException {
            return Request.get(path).header(ACCEPT, BROWSER_ACCEPT);
        }
    }

    /**
     * Which end of the exchange a body failure is charged to, and how much of it a production
     * deployment still tells the sender. Either body can be read in either flow, so the exception
     * decides rather than the flow it surfaced in.
     */
    @Nested
    class bodyFailures {

        private static final String TRUNCATED_GZIP = "Unexpected end of ZLIB input stream";

        @Test
        @DisplayName("An undecodable request body is the sender's error, naming the coding that failed")
        void undecodableRequestBody() throws Exception {
            Exchange exc = Request.post("/").body("x").buildExchange();

            Response r = bodyFailure(false, "xml protection", exc, decodingFailure(exc.getRequest())).build();

            assertEquals(400, r.getStatusCode());
            JsonNode json = parseJson(r);
            assertEquals("Request body could not be decoded", json.get(TITLE).asText());
            assertEquals("https://membrane-api.io/problems/user/body-decoding", json.get(TYPE).asText());
            assertEquals("gzip", json.get("contentEncoding").asText());
            assertTrue(json.get(DETAIL).asText().contains(TRUNCATED_GZIP), json.get(DETAIL).asText());
        }

        @Test
        @DisplayName("The same failure on a backend response body is ours, not the sender's")
        void undecodableResponseBody() throws Exception {
            Exchange exc = Request.post("/").body("x").buildExchange();
            exc.setResponse(Response.ok().body("x").build());

            Response r = bodyFailure(false, "validator", exc, decodingFailure(exc.getResponse())).build();

            assertEquals(500, r.getStatusCode());
            // Nothing the sender can act on, so it is not dressed up as a decoding problem of theirs
            assertEquals("https://membrane-api.io/problems/internal", parseJson(r).get(TYPE).asText());
        }

        @Test
        @DisplayName("A body failure that is not a decoding failure keeps its own cause and plain type")
        void readFailureWithoutDecoding() throws Exception {
            Exchange exc = Request.post("/").body("x").buildExchange();
            var failure = new ReadingBodyException(new EOFException("peer went away"), exc.getRequest());

            Response r = bodyFailure(false, "openapi", exc, failure).build();

            assertEquals(400, r.getStatusCode());
            JsonNode json = parseJson(r);
            assertEquals("peer went away", json.get(DETAIL).asText());
            assertEquals("https://membrane-api.io/problems/user", json.get(TYPE).asText());
        }

        @Test
        @DisplayName("Production keeps the title, type and coding but withholds the decoder's wording")
        void production() throws Exception {
            Exchange exc = Request.post("/").body("x").buildExchange();

            Response r = bodyFailure(true, "xml protection", exc, decodingFailure(exc.getRequest())).build();

            assertEquals(400, r.getStatusCode());
            JsonNode json = parseJson(r);
            assertEquals("Request body could not be decoded", json.get(TITLE).asText());
            assertEquals("https://membrane-api.io/problems/user/body-decoding", json.get(TYPE).asText());
            assertEquals("gzip", json.get("contentEncoding").asText());
            assertFalse(r.getBodyAsStringDecoded().contains(TRUNCATED_GZIP), r.getBodyAsStringDecoded());
        }

        /**
         * {@link ProblemDetails#addSubSee} concatenates without a separator, so the factory has to be
         * the only one adding a suffix - a caller adding its own would run the two together.
         */
        @Test
        void seeSuffixIsSetOnceAndWellFormed() throws Exception {
            Exchange exc = Request.post("/").body("x").buildExchange();

            Response r = bodyFailure(false, "openapi", exc, decodingFailure(exc.getRequest())).build();

            assertTrue(parseJson(r).get(SEE).asText().endsWith("/reading-body"), parseJson(r).get(SEE).asText());
        }

        private static ReadingBodyException decodingFailure(Message source) {
            return new ReadingBodyException(new DecodingException("gzip", new EOFException(TRUNCATED_GZIP)), source);
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