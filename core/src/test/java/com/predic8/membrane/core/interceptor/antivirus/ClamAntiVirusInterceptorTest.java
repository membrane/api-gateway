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
import com.predic8.membrane.core.http.Response;
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
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClamAntiVirusInterceptorTest {

    private final ClamAntiVirusInterceptor interceptor = new ClamAntiVirusInterceptor();
    private final ClamAVClient client = mock(ClamAVClient.class);
    private final DummyTestRouter router = new DummyTestRouter();
    private final Logger logger = (Logger) LogManager.getLogger(ClamAntiVirusInterceptor.class);
    private final TestAppender appender = new TestAppender("clamav-test");
    private Exchange exchange;

    @BeforeEach
    void setUp() throws Exception {
        appender.start();
        logger.addAppender(appender);
        router.getConfiguration().setProduction(false);
        interceptor.init(router);
        interceptor.client = client;
        exchange = post("/scan").header("Accept", "application/json").body("request content").buildExchange();
        exchange.setResponse(Response.ok().body("response content ä").build());
    }

    @AfterEach
    void tearDown() {
        logger.removeAppender(appender);
        appender.stop();
    }

    @Test
    void cleanContentContinuesWithoutReplacingResponse() throws Exception {
        when(client.scan(any(InputStream.class))).thenReturn("stream: OK\0".getBytes(US_ASCII));
        var response = exchange.getResponse();

        assertEquals(CONTINUE, interceptor.handleResponse(exchange));
        assertSame(response, exchange.getResponse());
        verify(client, times(2)).scan(any(InputStream.class));
    }

    @Test
    void detectedVirusIsReportedAsSecurityProblemInsteadOfDaemonFailure() throws Exception {
        // Regression for https://github.com/membrane/api-gateway/issues/3386:
        // headers are clean, but scanning the body returns a virus signature.
        when(client.scan(any(InputStream.class))).thenReturn(
                "stream: OK\0".getBytes(US_ASCII),
                "stream: Win.Test.EICAR_HDB-1 FOUND\0".getBytes(US_ASCII));

        assertEquals(RETURN, interceptor.handleResponse(exchange));
        verify(client, times(2)).scan(any(InputStream.class));
        var body = exchange.getResponse().getBodyAsStringDecoded();
        var problem = new ObjectMapper().readTree(body);
        assertEquals(500, exchange.getResponse().getStatusCode(), body);
        assertEquals("Request blocked", problem.path("title").asText());
        assertEquals("The request contains potentially harmful content.", problem.path("detail").asText());
        assertTrue(appender.contains("Win.Test.EICAR_HDB-1 FOUND"));
        assertFalse(appender.contains("Could not execute virus scan"));
        assertAll(
                () -> assertEquals("https://membrane-api.io/problems/security/potentially-harmful-content",
                        problem.path("type").asText()),
                () -> assertFalse(body.contains("Could not reach clamav daemon"),
                        "A successful virus detection must not be reported as an unreachable daemon"));
    }

    @Test
    void unreachableDaemonIsReportedAsScannerFailure() throws Exception {
        when(client.scan(any(InputStream.class))).thenThrow(new IOException("Connection refused"));

        assertEquals(RETURN, interceptor.handleResponse(exchange));
        assertEquals(500, exchange.getResponse().getStatusCode());
        var problem = new ObjectMapper().readTree(exchange.getResponse().getBodyAsStringDecoded());
        assertEquals("https://membrane-api.io/problems/internal", problem.path("type").asText());
        assertEquals("Request processing failed", problem.path("title").asText());
        assertEquals("Could not execute virus scan.", problem.path("detail").asText());
        assertTrue(appender.contains("Could not execute virus scan"));
        assertFalse(appender.contains("detected malicious content"));
    }

    @Test
    void scansResponseHeadersAndBody() throws Exception {
        List<String> scanned = new ArrayList<>();
        when(client.scan(any(InputStream.class))).thenAnswer(invocation -> {
            InputStream input = invocation.getArgument(0);
            scanned.add(new String(input.readAllBytes(), UTF_8));
            return "stream: OK\0".getBytes(US_ASCII);
        });
        var expectedHeaders = exchange.getResponse().getHeader().toString();

        assertEquals(CONTINUE, interceptor.handleResponse(exchange));
        assertEquals(List.of(expectedHeaders, "response content ä"), scanned);
    }

    @Test
    void virusInHeadersIsBlockedAsSecurityProblemInProduction() throws Exception {
        router.getConfiguration().setProduction(true);
        when(client.scan(any(InputStream.class))).thenReturn("stream: Win.Test.EICAR_HDB-1 FOUND\0".getBytes(US_ASCII));

        assertEquals(RETURN, interceptor.handleResponse(exchange));
        assertEquals(500, exchange.getResponse().getStatusCode());
        var problem = new ObjectMapper().readTree(exchange.getResponse().getBodyAsStringDecoded());
        assertEquals("https://membrane-api.io/problems/security", problem.path("type").asText());
        assertTrue(appender.contains("Win.Test.EICAR_HDB-1 FOUND"));
        verify(client).scan(any(InputStream.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stream: scanning failed ERROR\0", "unexpected reply\0"})
    void scannerErrorReplyIsNotReportedAsVirus(String reply) throws Exception {
        when(client.scan(any(InputStream.class))).thenReturn(reply.getBytes(US_ASCII));

        assertEquals(RETURN, interceptor.handleResponse(exchange));
        assertEquals(500, exchange.getResponse().getStatusCode());
        var problem = new ObjectMapper().readTree(exchange.getResponse().getBodyAsStringDecoded());
        assertEquals("https://membrane-api.io/problems/internal", problem.path("type").asText());
        assertFalse(appender.contains("detected malicious content"));
    }

    @ParameterizedTest
    @CsvSource({
            "false, application/json", "true, application/json",
            "false, application/xml", "true, application/xml",
            "false, text/html", "true, text/html"
    })
    void responsesDoNotExposeScannerDetails(boolean production, String accept) throws Exception {
        router.getConfiguration().setProduction(production);
        exchange.getRequest().getHeader().setValue("Accept", accept);
        when(client.scan(any(InputStream.class)))
                .thenReturn("stream: Win.Test.EICAR_HDB-1 FOUND\0".getBytes(US_ASCII))
                .thenThrow(new IOException("ClamAV scanner antivirus.internal:3310 unavailable"));

        assertEquals(RETURN, interceptor.handleResponse(exchange));
        assertNoScannerDetails();

        exchange.setResponse(Response.ok().body("response content").build());
        assertEquals(RETURN, interceptor.handleResponse(exchange));
        if (production)
            assertNoScannerDetails();
    }

    private void assertNoScannerDetails() {
        var body = exchange.getResponse().getBodyAsStringDecoded().toLowerCase(Locale.ROOT);
        for (String sensitive : List.of("virus", "scan", "clam", "daemon", "eicar", "3310", "localhost")) {
            assertFalse(body.contains(sensitive), "Response exposes " + sensitive + ": " + body);
        }
    }
}
