/* Copyright 2026 predic8 GmbH, www.predic8.com

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
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicReference;

import static com.predic8.membrane.core.exceptions.BodyFailureProblems.bodyFailure;
import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.test.TestAppender.captureLog;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Which end of the exchange a body failure is charged to, and how much of it a production
 * deployment still tells the sender. Either body can be read in either flow, so the exception
 * decides rather than the flow it surfaced in.
 */
class BodyFailureProblemsTest {

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
    @DisplayName("A backend body cut off by the connection names the place and needs no stack trace")
    void socketFailureOnBackendBody() throws Exception {
        Exchange exc = Request.post("/").body("x").buildExchange();
        exc.setResponse(Response.ok().body("x").build());
        var failure = new ReadingBodyException(new SocketException("Connection reset"), exc.getResponse());

        JsonNode json = parseJson(bodyFailure(false, "validator", exc, failure).build());

        assertEquals("Could not read the response body from the backend.", json.get(DETAIL).asText());
        assertEquals("java.net.SocketException: Connection reset", json.get("message").asText());
        assertFalse(json.has("stackTrace"), json.toString());
    }

    @Test
    @DisplayName("A backend body failure that is not an I/O error keeps its stack trace")
    void otherFailureOnBackendBodyKeepsStackTrace() throws Exception {
        Exchange exc = Request.post("/").body("x").buildExchange();
        exc.setResponse(Response.ok().body("x").build());
        var failure = new ReadingBodyException(new IllegalStateException("decoder bug"), exc.getResponse());

        assertTrue(parseJson(bodyFailure(false, "validator", exc, failure).build()).has("stackTrace"));
    }

    @Test
    @DisplayName("Production hides a failure on a backend body behind a log key")
    void productionGivesLogKeyForBackendBody() throws Exception {
        Exchange exc = Request.post("/").body("x").buildExchange();
        exc.setResponse(Response.ok().body("x").build());
        var failure = new ReadingBodyException(new SocketException("Connection reset"), exc.getResponse());

        JsonNode json = parseJson(bodyFailure(true, "validator", exc, failure).build());

        assertTrue(json.get(DETAIL).asText().contains("See server log (key:"), json.toString());
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

    @Test
    @DisplayName("Production logs the withheld reason under the key it gives the sender, without a stack trace")
    void productionLogsRequestBodyFailureUnderLogKey() throws Exception {
        final var exc = Request.post("/").body("x").buildExchange();
        final var response = new AtomicReference<Response>();

        final var log = captureLog(() -> {
            response.set(bodyFailure(true, "openapi", exc, decodingFailure(exc.getRequest())).build());
            return null;
        });

        final var json = parseJson(response.get());
        assertTrue(json.hasNonNull(DETAIL) && json.get(DETAIL).asText().contains("See server log (key:"), json.toString());
        assertTrue(log.contains(TRUNCATED_GZIP), log.getMessages().toString());
        assertFalse(log.contains("Stacktrace for hidden details"), log.getMessages().toString());
    }

    @Test
    @DisplayName("Production tells the sender that its Content-Type could not be parsed, but not the parser's wording")
    void unparsableContentTypeInProduction() throws Exception {
        Exchange exc = Request.post("/").body("x").buildExchange();
        var failure = new ReadingBodyException(new InvalidContentTypeException("application/json; charset",
                new jakarta.mail.internet.ParseException("In parameter list <; charset>, expected '=', got \"null\"")),
                exc.getRequest());

        Response r = bodyFailure(true, "validator", exc, failure).build();

        assertEquals(400, r.getStatusCode());
        JsonNode json = parseJson(r);
        assertEquals("https://membrane-api.io/problems/user/content-type", json.get(TYPE).asText());
        assertEquals("Content-Type header could not be parsed", json.get(TITLE).asText());
        // The parser's reason is internal, so production appends the log key
        assertTrue(json.get(DETAIL).asText().startsWith("The Content-Type header \"application/json; charset\" could not be parsed. "),
                json.get(DETAIL).asText());
        assertFalse(r.getBodyAsStringDecoded().contains("expected '='"), r.getBodyAsStringDecoded());
    }

    @Test
    @DisplayName("Production escapes control characters of an unparsable Content-Type before logging it")
    void unparsableContentTypeIsEscapedInLog() throws Exception {
        final var exc = Request.post("/").body("x").buildExchange();
        final var failure = new ReadingBodyException(new InvalidContentTypeException("application/json; charset\u001b[31m",
                new jakarta.mail.internet.ParseException("In parameter list <; charset\u001b[31m>, expected '=', got \"null\"")),
                exc.getRequest());

        final var log = captureLog(() -> bodyFailure(true, "validator", exc, failure).build());

        assertTrue(log.contains("could not be parsed"), log.getMessages().toString());
        assertFalse(log.contains("\u001b"), log.getMessages().toString());
    }

    @Test
    @DisplayName("Production tells the sender why its XOP package could not be reassembled")
    void xopReassemblyFailureInProduction() throws Exception {
        Exchange exc = Request.post("/").body("x").buildExchange();
        var failure = new ReadingBodyException(new com.predic8.membrane.core.multipart.XopReassemblyException(
                "Did not find multipart with id bin@test."), exc.getRequest());

        Response r = bodyFailure(true, "validator", exc, failure).build();

        assertEquals(400, r.getStatusCode());
        JsonNode json = parseJson(r);
        assertEquals("https://membrane-api.io/problems/user/xop", json.get(TYPE).asText());
        assertEquals("XOP package could not be reassembled", json.get(TITLE).asText());
        assertEquals("Did not find multipart with id bin@test.", json.get(DETAIL).asText());
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

    private static JsonNode parseJson(Response r) throws Exception {
        return new ObjectMapper().readTree(r.getBodyAsStringDecoded());
    }
}
