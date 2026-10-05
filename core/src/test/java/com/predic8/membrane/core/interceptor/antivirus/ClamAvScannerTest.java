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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.test.TestAppender;
import fi.solita.clamav.ClamAVClient;
import fi.solita.clamav.ClamAVSizeLimitException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.antivirus.ScanResult.CLEAN;
import static com.predic8.membrane.core.interceptor.antivirus.ScanResult.INFECTED;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClamAvScannerTest {
    private final ClamAVClient client = mock(ClamAVClient.class);
    private final ContentScanner scanner = new ClamAvScanner(client);
    private Exchange exchange;

    @BeforeEach
    void setUp() throws Exception {
        exchange = post("/scan").body("request content").buildExchange();
        exchange.setResponse(Response.ok().body("response content ä").build());
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

        assertEquals(CLEAN, scanner.scan(exchange.getResponse()));
        assertEquals(List.of(expectedHeaders, "response content ä"), scanned);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 204, 205, 304})
    void doesNotDecodeOrValidateBodylessResponseMetadata(int status) throws Exception {
        var response = Response.ok().status(status)
                .header("Content-Encoding", "gzip")
                .contentType("multipart/mixed") // Deliberately has no boundary.
                .build();
        when(client.scan(any(InputStream.class))).thenReturn("stream: OK\0".getBytes(US_ASCII));

        assertEquals(CLEAN, scanner.scan(response));
        verify(client).scan(any(InputStream.class)); // Headers are still scanned.
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void scansBinaryMessageBodyWithoutChangingBytes(boolean gzip, boolean request) throws Exception {
        // Include bytes that cannot survive decoding as UTF-8 and encoding back to bytes.
        byte[] body = {0x00, 0x41, (byte) 0x80, (byte) 0xFF, (byte) 0xC3, 0x28};
        byte[] responseBody = body;
        if (gzip) {
            var compressed = new ByteArrayOutputStream();
            try (var output = new GZIPOutputStream(compressed)) {
                output.write(body);
            }
            responseBody = compressed.toByteArray();
        }
        var response = Response.ok().contentType("application/octet-stream").body(responseBody).build();
        if (gzip)
            response.getHeader().setValue("Content-Encoding", "gzip");
        var message = setScannedMessage(response, request);
        List<byte[]> scanned = new ArrayList<>();
        when(client.scan(any(InputStream.class))).thenAnswer(invocation -> {
            InputStream input = invocation.getArgument(0);
            scanned.add(input.readAllBytes());
            return "stream: OK\0".getBytes(US_ASCII);
        });

        assertEquals(CLEAN, scanner.scan(request ? exchange.getRequest() : exchange.getResponse()));
        assertEquals(2, scanned.size(), "Both message headers and body must be scanned");
        assertArrayEquals(body, scanned.get(1), "ClamAV must receive the original binary body bytes");
        assertSame(message, request ? exchange.getRequest() : exchange.getResponse());
        assertArrayEquals(responseBody, message.getBody().getContent(),
                "Scanning must preserve the original message bytes");
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void scansDecodedMultipartPartsIncludingNestedAttachments(boolean gzip, boolean request) throws Exception {
        byte[] binary = {0, (byte) 0x80, (byte) 0xff, (byte) 0xc3, 0x28};
        String nested = multipart("inner",
                "Content-Type: application/octet-stream\r\nContent-Transfer-Encoding: base64\r\n\r\n"
                        + Base64.getEncoder().encodeToString(binary));
        String body = multipart("outer",
                "Content-Type: application/xop+xml\r\n\r\n<Envelope/>",
                "Content-Type: multipart/mixed; boundary=inner\r\n\r\n" + nested,
                "Content-Type: text/plain\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\nhello=20world=FF");
        byte[] responseBody = body.getBytes(UTF_8);
        if (gzip) {
            var compressed = new ByteArrayOutputStream();
            try (var output = new GZIPOutputStream(compressed)) {
                output.write(responseBody);
            }
            responseBody = compressed.toByteArray();
        }
        var response = Response.ok().contentType("multipart/related; boundary=outer").body(responseBody).build();
        if (gzip)
            response.getHeader().setValue("Content-Encoding", "gzip");
        var message = setScannedMessage(response, request);
        List<byte[]> scanned = new ArrayList<>();
        when(client.scan(any(InputStream.class))).thenAnswer(invocation -> {
            InputStream input = invocation.getArgument(0);
            scanned.add(input.readAllBytes());
            return "stream: OK\0".getBytes(US_ASCII);
        });

        assertEquals(CLEAN, scanner.scan(request ? exchange.getRequest() : exchange.getResponse()));
        assertEquals(5, scanned.size()); // Headers, complete MIME body, XML, binary attachment, text part.
        assertTrue(new String(scanned.get(1), UTF_8).contains("Content-Transfer-Encoding: base64"));
        assertArrayEquals(body.getBytes(UTF_8), scanned.get(1));
        assertArrayEquals("<Envelope/>".getBytes(UTF_8), scanned.get(2));
        assertArrayEquals(binary, scanned.get(3));
        assertArrayEquals(new byte[]{'h', 'e', 'l', 'l', 'o', ' ', 'w', 'o', 'r', 'l', 'd', (byte) 0xff}, scanned.get(4));
        assertSame(message, request ? exchange.getRequest() : exchange.getResponse());
        assertArrayEquals(responseBody, message.getBody().getContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"multipart/form-data", "multipart/related", "multipart/mixed"})
    void blocksVirusInDecodedAttachment(String contentType) throws Exception {
        byte[] signature = "test-virus".getBytes(US_ASCII);
        String body = multipart("outer",
                "Content-Type: text/plain\r\n\r\nclean",
                "Content-Transfer-Encoding: base64\r\n\r\n" + Base64.getEncoder().encodeToString(signature),
                "Content-Type: text/plain\r\n\r\nunvisited");
        exchange.setResponse(Response.ok().contentType(contentType + "; boundary=outer").body(body).build());
        when(client.scan(any(InputStream.class))).thenAnswer(invocation -> {
            InputStream input = invocation.getArgument(0);
            return (Arrays.equals(signature, input.readAllBytes()) ? "stream: Test FOUND\0" : "stream: OK\0")
                    .getBytes(US_ASCII);
        });

        assertEquals(INFECTED, scanner.scan(exchange.getResponse()));
        verify(client, times(4)).scan(any(InputStream.class));
    }

    @Test
    void blocksVirusSignatureInMultipartPartHeader() throws Exception {
        exchange.setResponse(Response.ok().contentType("multipart/mixed; boundary=outer").body(
                multipart("outer", "X-Test: test-virus\r\n\r\nclean")).build());
        when(client.scan(any(InputStream.class))).thenAnswer(invocation -> {
            InputStream input = invocation.getArgument(0);
            return (new String(input.readAllBytes(), US_ASCII).contains("test-virus")
                    ? "stream: Test FOUND\0" : "stream: OK\0").getBytes(US_ASCII);
        });

        assertEquals(INFECTED, scanner.scan(exchange.getResponse()));
        verify(client, times(2)).scan(any(InputStream.class)); // Message headers, then full MIME body.
    }

    @Test
    void scansDecodedPartsWhenCompleteMultipartBodyExceedsSizeLimit() throws Exception {
        byte[] signature = "test-virus".getBytes(US_ASCII);
        exchange.setResponse(Response.ok().contentType("multipart/mixed; boundary=outer").body(multipart("outer",
                "Content-Type: text/plain\r\n\r\nclean",
                "Content-Transfer-Encoding: base64\r\n\r\n" + Base64.getEncoder().encodeToString(signature))).build());
        when(client.scan(any(InputStream.class))).thenAnswer(invocation -> {
            byte[] content = ((InputStream) invocation.getArgument(0)).readAllBytes();
            if (new String(content, US_ASCII).startsWith("--outer"))
                throw new ClamAVSizeLimitException("Clamd size limit exceeded");
            return (Arrays.equals(signature, content) ? "stream: Test FOUND\0" : "stream: OK\0").getBytes(US_ASCII);
        });

        assertEquals(INFECTED, scanner.scan(exchange.getResponse()));
        verify(client, times(4)).scan(any(InputStream.class)); // Headers, complete body, both parts.
    }

    @Test
    void sizeLimitFailureIsPropagatedWhenNoDecodedPartIsInfected() throws Exception {
        exchange.setResponse(Response.ok().contentType("multipart/mixed; boundary=outer").body(multipart("outer",
                "Content-Type: text/plain\r\n\r\nlarge",
                "Content-Type: text/plain\r\n\r\nclean")).build());
        var failure = new ClamAVSizeLimitException("Clamd size limit exceeded");
        when(client.scan(any(InputStream.class))).thenAnswer(invocation -> {
            String content = new String(((InputStream) invocation.getArgument(0)).readAllBytes(), US_ASCII);
            if (content.startsWith("--outer") || content.equals("large"))
                throw failure;
            return "stream: OK\0".getBytes(US_ASCII);
        });

        assertSame(failure, assertThrows(ClamAVSizeLimitException.class, () -> scanner.scan(exchange.getResponse())));
        verify(client, times(4)).scan(any(InputStream.class)); // A too-large part does not stop the other parts.
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not multipart",
            "--outer\r\nContent-Type: text/plain\r\n\r\ntruncated",
            "--outer\r\nContent-Transfer-Encoding: base64\r\n\r\n%%%\r\n--outer--\r\n",
            "--outer\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\n=ZZ\r\n--outer--\r\n",
            "--outer\r\nContent-Transfer-Encoding: unknown\r\n\r\ndata\r\n--outer--\r\n"
    })
    void malformedMultipartIsBlocked(String body) throws Exception {
        exchange.setResponse(Response.ok().contentType("multipart/mixed; boundary=outer").body(body).build());

        assertThrows(InvalidScanContentException.class, () -> scanner.scan(exchange.getResponse()));
        verifyNoInteractions(client);
    }

    @Test
    void malformedContentEncodingIsInvalidContent() throws Exception {
        exchange.setResponse(Response.ok().body("not gzip").build());
        exchange.getResponse().getHeader().setValue("Content-Encoding", "gzip");
        when(client.scan(any(InputStream.class))).thenAnswer(invocation -> {
            ((InputStream) invocation.getArgument(0)).readAllBytes();
            return "stream: OK\0".getBytes(US_ASCII);
        });

        assertThrows(InvalidScanContentException.class, () -> scanner.scan(exchange.getResponse()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stream: scanning failed ERROR\0", "unexpected reply\0"})
    void scannerErrorReplyIsNotReportedAsVirus(String reply) throws Exception {
        when(client.scan(any(InputStream.class))).thenReturn(reply.getBytes(US_ASCII));
        assertThrows(IOException.class, () -> scanner.scan(exchange.getResponse()));
    }

    @Test
    void propagatesDaemonFailure() throws Exception {
        var failure = new IOException("Connection refused");
        when(client.scan(any(InputStream.class))).thenThrow(failure);
        assertSame(failure, assertThrows(IOException.class, () -> scanner.scan(exchange.getResponse())));
    }

    @Test
    void infectedHeadersSkipBodyAndLogDetection() throws Exception {
        var logger = (Logger) LogManager.getLogger(ClamAvScanner.class);
        var appender = new TestAppender("clamav-scanner-test");
        appender.start();
        logger.addAppender(appender);
        try {
            when(client.scan(any(InputStream.class))).thenReturn("stream: Test FOUND\0".getBytes(US_ASCII));
            assertEquals(INFECTED, scanner.scan(exchange.getResponse()));
            verify(client).scan(any(InputStream.class));
            assertTrue(appender.contains("Test FOUND"));
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    private Message setScannedMessage(Response response, boolean request) throws Exception {
        if (!request) {
            exchange.setResponse(response);
            return response;
        }
        exchange = post("/scan").body(response.getBody().getContent()).buildExchange();
        exchange.getRequest().setHeader(response.getHeader());
        exchange.getRequest().getHeader().setValue("Accept", "application/json");
        return exchange.getRequest();
    }

    private static String multipart(String boundary, String... parts) {
        return "--" + boundary + "\r\n" + String.join("\r\n--" + boundary + "\r\n", parts)
                + "\r\n--" + boundary + "--\r\n";
    }

}
