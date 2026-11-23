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
import java.io.StringReader;
import java.net.URISyntaxException;
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
        void publicDetailIsShown() throws Exception {
            final var j = parseJson(security(false, "a")
                    .status(403)
                    .detail("Public text.", true)
                    .internal("origin", "https://evil.example")
                    .build());

            assertEquals("Public text.", j.get(DETAIL).asText());
            assertEquals("https://evil.example", j.get("origin").asText());
        }

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
            assertEquals("Internal Server Error", j.get(TITLE).asText());
        }

        @Test
        void masksTitleWithReasonPhraseOfStatus() throws Exception {
            final var j = parseJson(gateway(true, "a")
                    .status(502)
                    .title("Backend backend.internal:8080 refused the connection")
                    .build());

            assertEquals("Bad Gateway", j.get(TITLE).asText());
        }

        @Test
        void publicDetailIsShown() throws Exception {
            final var j = parseJson(security(true, "a")
                    .status(401)
                    .title("Access Denied")
                    .detail("Request requires an API key.", true)
                    .build());

            assertEquals(4, j.size());
            assertEquals("Request requires an API key.", j.get(DETAIL).asText());
        }

        @Test
        void publicDetailWithInternalsKeepsTextAndAddsLogKey() throws Exception {
            final var j = parseJson(security(true, "a")
                    .status(403)
                    .detail("Origin not allowed by CORS policy.", true)
                    .internal("origin", "https://evil.example")
                    .build());

            assertFalse(j.has("origin"));
            final var detail = j.get(DETAIL).asText();
            assertTrue(detail.startsWith("Origin not allowed by CORS policy. Internal details are hidden."), detail);
            assertTrue(detail.contains("key"), detail);
            assertFalse(detail.contains("evil"), detail);
        }

        @Test
        @DisplayName("A public detail does not make an attached exception irrelevant: production still logs it under a key")
        void publicDetailWithExceptionGetsLogKey() throws Exception {
            final var j = parseJson(user(true, "a")
                    .detail("Did not find multipart with id bin@example.com.", true)
                    .exception(new IllegalStateException("decoder bug"))
                    .build());

            assertTrue(j.get(DETAIL).asText().startsWith("Did not find multipart with id bin@example.com. "), j.get(DETAIL).asText());
            assertTrue(j.get(DETAIL).asText().contains("See server log (key:"), j.get(DETAIL).asText());
        }

        @Test
        void laterDetailMakesDetailInternalAgain() throws Exception {
            final var j = parseJson(security(true, "a")
                    .status(403)
                    .detail("Public text.", true)
                    .detail("Internal text.", false)
                    .build());

            assertFalse(j.has(DETAIL));
        }

        @Test
        void nullPublicDetailIsOmitted() throws Exception {
            final var j = parseJson(security(true, "a")
                    .status(403)
                    .detail(null, true)
                    .build());

            assertFalse(j.has(DETAIL));
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
            assertTrue(body.contains("https://github.com/membrane/api-gateway"), body);
        }

        @Test
        @DisplayName("The see URL is rendered as a link")
        void seeIsLink() throws Exception {
            Exchange exc = get("/foo").buildExchange();

            user(false, "openapi-publisher")
                    .addSubType("openapi")
                    .addSubSee("wrong-id")
                    .status(404)
                    .buildAndSetResponse(exc);

            String body = exc.getResponse().getBodyAsStringDecoded();
            String url = "https://membrane-api.io/problems/user/openapi/openapi-publisher/wrong-id";
            assertTrue(body.contains("<a href=\"" + url + "\">" + url + "</a>"), body);
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
            assertTrue(body.contains("<span class=\"key\">foo:</span> 7"), body);
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
        @DisplayName("HTML the client explicitly rejected is not served to it")
        void htmlRejectedByQualityZero() throws Exception {
            Exchange exc = Request.get("/foo").header(ACCEPT, "text/html;q=0").buildExchange();

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