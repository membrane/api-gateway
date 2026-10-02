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

package com.predic8.membrane.core.interceptor.antivirus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.test.TestAppender;
import fi.solita.clamav.ClamAVClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.AbstractInterceptor.getMessage;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.antivirus.ScanResult.CLEAN;
import static com.predic8.membrane.core.interceptor.antivirus.ScanResult.INFECTED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClamAntiVirusInterceptorTest {
    private final ClamAntiVirusInterceptor interceptor = new ClamAntiVirusInterceptor();
    private final ContentScanner scanner = mock(ContentScanner.class);
    private final DummyTestRouter router = new DummyTestRouter();
    private final Logger logger = (Logger) LogManager.getLogger(ClamAntiVirusInterceptor.class);
    private final TestAppender appender = new TestAppender("clamav-interceptor-test");
    private Exchange exchange;

    @BeforeEach
    void setUp() throws Exception {
        appender.start();
        logger.addAppender(appender);
        router.getConfiguration().setProduction(false);
        interceptor.init(router);
        interceptor.scanner = scanner;
        exchange = post("/scan").header("Accept", "application/json").body("request content").buildExchange();
        exchange.setResponse(Response.ok().body("response content").build());
    }

    @AfterEach
    void tearDown() {
        logger.removeAppender(appender);
        appender.stop();
    }

    @ParameterizedTest
    @EnumSource(value = Flow.class, names = {"REQUEST", "RESPONSE"})
    void cleanMessageContinuesUnchanged(Flow flow) throws Exception {
        if (flow == REQUEST)
            exchange.setResponse(null);
        var message = getMessage(exchange, flow);
        when(scanner.scan(message)).thenReturn(CLEAN);

        assertEquals(CONTINUE, handle(flow));
        assertSame(message, getMessage(exchange, flow));
        if (flow == REQUEST)
            assertNull(exchange.getResponse());
        verify(scanner).scan(same(message));
        verifyNoMoreInteractions(scanner);
    }

    @ParameterizedTest
    @EnumSource(value = Flow.class, names = {"REQUEST", "RESPONSE"})
    void detectionProducesDirectionSpecificSecurityProblem(Flow flow) throws Exception {
        if (flow == REQUEST)
            exchange.setResponse(null);
        var message = getMessage(exchange, flow);
        when(scanner.scan(message)).thenReturn(INFECTED);

        assertEquals(RETURN, handle(flow));
        assertEquals(500, exchange.getResponse().getStatusCode());
        var problem = new ObjectMapper().readTree(exchange.getResponse().getBodyAsStringDecoded());
        assertEquals(flow == REQUEST ? "Request blocked" : "Response blocked", problem.path("title").asText());
        assertEquals(flow == REQUEST ? "The request contains potentially harmful content."
                : "The response contains potentially harmful content.", problem.path("detail").asText());
        assertEquals("https://membrane-api.io/problems/security/potentially-harmful-content", problem.path("type").asText());
        assertFalse(appender.contains("Could not execute virus scan"));
        verify(scanner).scan(same(message));
    }

    @ParameterizedTest
    @EnumSource(value = Flow.class, names = {"REQUEST", "RESPONSE"})
    void scannerFailureBlocksMessageAndLogsError(Flow flow) throws Exception {
        assertEquals(ClamAntiVirusInterceptor.ScanFailureAction.BLOCK, interceptor.getOnScanFailure());
        when(scanner.scan(any(Message.class))).thenThrow(new IOException("Connection refused"));

        assertEquals(RETURN, handle(flow));
        assertEquals(500, exchange.getResponse().getStatusCode());
        var problem = new ObjectMapper().readTree(exchange.getResponse().getBodyAsStringDecoded());
        assertEquals("https://membrane-api.io/problems/internal", problem.path("type").asText());
        assertEquals("Request processing failed", problem.path("title").asText());
        assertEquals("Could not execute virus scan.", problem.path("detail").asText());
        assertTrue(appender.contains("Could not execute virus scan"));
    }

    @ParameterizedTest
    @EnumSource(value = Flow.class, names = {"REQUEST", "RESPONSE"})
    void passOnScanFailurePreservesMessage(Flow flow) throws Exception {
        interceptor.setOnScanFailure(ClamAntiVirusInterceptor.ScanFailureAction.PASS);
        if (flow == REQUEST)
            exchange.setResponse(null);
        var message = getMessage(exchange, flow);
        var originalResponse = exchange.getResponse();
        byte[] body = message.getBody().getContent();
        String headers = message.getHeader().toString();
        when(scanner.scan(message)).thenThrow(new IOException("Connection refused"));

        assertEquals(CONTINUE, handle(flow));
        assertSame(message, getMessage(exchange, flow));
        assertSame(originalResponse, exchange.getResponse());
        assertArrayEquals(body, message.getBody().getContent());
        assertEquals(headers, message.getHeader().toString());
        assertTrue(appender.contains("passing message without a completed scan"));
    }

    @ParameterizedTest
    @EnumSource(value = Flow.class, names = {"REQUEST", "RESPONSE"})
    void passOnScanFailureStillBlocksDetections(Flow flow) throws Exception {
        interceptor.setOnScanFailure(ClamAntiVirusInterceptor.ScanFailureAction.PASS);
        detectionProducesDirectionSpecificSecurityProblem(flow);
    }

    @Test
    void scanFailureActionCannotBeNull() {
        assertThrows(NullPointerException.class, () -> interceptor.setOnScanFailure(null));
    }

    @ParameterizedTest
    @CsvSource({"REQUEST, block", "REQUEST, pass", "RESPONSE, block", "RESPONSE, pass"})
    void invalidMultipartAlwaysBlocksBeforeContactingScanner(Flow flow, String action) throws Exception {
        interceptor.setOnScanFailure(ClamAntiVirusInterceptor.ScanFailureAction.valueOf(action.toUpperCase(Locale.ROOT)));
        var client = mock(ClamAVClient.class);
        interceptor.scanner = new ClamAvScanner(client);
        var message = getMessage(exchange, flow);
        message.getHeader().setContentType("multipart/mixed; boundary=outer");
        // A clean first part must not hide an invalid later part when the scanner is offline.
        message.setBodyContent(("--outer\r\nContent-Type: text/plain\r\n\r\nclean\r\n"
                + "--outer\r\nContent-Transfer-Encoding: base64\r\n\r\n%%%\r\n--outer--\r\n")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        when(client.scan(any(InputStream.class))).thenThrow(new IOException("Connection refused"));

        assertEquals(RETURN, handle(flow));
        assertEquals(500, exchange.getResponse().getStatusCode());
        assertFalse(appender.contains("passing message without a completed scan"));
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @EnumSource(value = Flow.class, names = {"REQUEST", "RESPONSE"})
    void validMultipartCanPassWhenScannerIsUnavailable(Flow flow) throws Exception {
        interceptor.setOnScanFailure(ClamAntiVirusInterceptor.ScanFailureAction.PASS);
        var client = mock(ClamAVClient.class);
        interceptor.scanner = new ClamAvScanner(client);
        var message = getMessage(exchange, flow);
        message.getHeader().setContentType("multipart/mixed; boundary=outer");
        byte[] body = "--outer\r\nContent-Type: text/plain\r\n\r\nclean\r\n--outer--\r\n"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        message.setBodyContent(body);
        when(client.scan(any(InputStream.class))).thenThrow(new IOException("Connection refused"));

        assertEquals(CONTINUE, handle(flow));
        assertSame(message, getMessage(exchange, flow));
        assertArrayEquals(body, message.getBody().getContent());
    }

    @ParameterizedTest
    @EnumSource(value = Flow.class, names = {"REQUEST", "RESPONSE"})
    void productionDetectionHidesSubtype(Flow flow) throws Exception {
        router.getConfiguration().setProduction(true);
        when(scanner.scan(any(Message.class))).thenReturn(INFECTED);

        assertEquals(RETURN, handle(flow));
        var problem = new ObjectMapper().readTree(exchange.getResponse().getBodyAsStringDecoded());
        assertEquals("https://membrane-api.io/problems/security", problem.path("type").asText());
        assertNoScannerDetails();
    }

    @ParameterizedTest
    @CsvSource({
            "REQUEST, false, application/json", "REQUEST, true, application/json",
            "REQUEST, false, application/xml", "REQUEST, true, application/xml",
            "REQUEST, false, text/html", "REQUEST, true, text/html",
            "RESPONSE, false, application/json", "RESPONSE, true, application/json",
            "RESPONSE, false, application/xml", "RESPONSE, true, application/xml",
            "RESPONSE, false, text/html", "RESPONSE, true, text/html"
    })
    void responsesDoNotExposeScannerDetails(Flow flow, boolean production, String accept) throws Exception {
        router.getConfiguration().setProduction(production);
        exchange.getRequest().getHeader().setValue("Accept", accept);
        when(scanner.scan(any(Message.class))).thenReturn(INFECTED)
                .thenThrow(new IOException("ClamAV scanner antivirus.internal:3310 unavailable"));

        assertEquals(RETURN, handle(flow));
        assertNoScannerDetails();
        exchange.setResponse(Response.ok().body("response content").build());
        assertEquals(RETURN, handle(flow));
        if (production)
            assertNoScannerDetails();
    }

    private Outcome handle(Flow flow) {
        return flow == REQUEST ? interceptor.handleRequest(exchange) : interceptor.handleResponse(exchange);
    }

    private void assertNoScannerDetails() {
        var body = exchange.getResponse().getBodyAsStringDecoded().toLowerCase(Locale.ROOT);
        for (String sensitive : List.of("virus", "scan", "clam", "daemon", "eicar", "3310", "localhost"))
            assertFalse(body.contains(sensitive), "Response exposes " + sensitive + ": " + body);
    }
}
