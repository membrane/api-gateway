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

package com.predic8.membrane.core.util;

import com.predic8.membrane.core.http.DecodingException;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.core.util.ZstdTestUtil.withZstdDecoding;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MessageUtilTest {

    @Test
    void validDeflateBodyReturnsOnlyDecompressedBytes() throws Exception {
        // Regression: Chunk.write() added HTTP chunk framing, e.g. "7\r\npayload\r\n".
        // Decoding must return only the original bytes, including for empty and multi-buffer bodies.
        for (String content : new String[]{"", "payload", "x".repeat(4096)}) {
            assertDecodesTo(content, rawDeflate(content));
        }
    }

    /**
     * RFC 9110 8.4.1.2 defines "deflate" as a zlib-wrapped (RFC 1950) stream; that is also what
     * Java's own {@link Deflater#Deflater(int)} produces by default. Only opting into raw DEFLATE
     * (RFC 1951, no zlib header) is the non-conformant case.
     */
    @Test
    void zlibWrappedDeflateBodyReturnsOnlyDecompressedBytes() throws Exception {
        for (String content : new String[]{"", "payload", "x".repeat(4096)}) {
            assertDecodesTo(content, zlibDeflate(content));
        }
    }

    /**
     * The decoded bytes are all there, only the Adler-32 checksum at the end of the zlib stream is
     * cut off.
     */
    @Test
    void truncatedZlibBodyWithoutAdlerTrailerIsRejected() throws Exception {
        byte[] compressed = zlibDeflate("payload");
        byte[] truncated = Arrays.copyOf(compressed, compressed.length - 1);

        assertDecodingFails("Truncated deflate stream.", truncated);
    }

    @Test
    void isZlibWrappedDetectsZlibHeaderOnly() {
        assertTrue(MessageUtil.isZlibWrapped(zlibDeflate("payload")));
        assertFalse(MessageUtil.isZlibWrapped(rawDeflate("payload")));
        assertFalse(MessageUtil.isZlibWrapped(new byte[0]));
        assertFalse(MessageUtil.isZlibWrapped(new byte[]{0x78}));
    }

    /**
     * A truncated raw deflate body leaves {@link java.util.zip.Inflater} unfinished and needing
     * input. MessageUtil must reject it instead of repeatedly calling inflate() with no progress.
     */
    @Test
    void truncatedDeflateBodyDoesNotLoopForever() throws Exception {
        byte[] compressed = rawDeflate("payload");
        byte[] truncated = Arrays.copyOf(compressed, compressed.length - 1);

        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertDecodingFails("Truncated deflate stream.", truncated));
    }

    /**
     * Membrane has no preset dictionary to offer, so a zlib stream that asks for one cannot be
     * decoded. It must fail rather than end silently as an empty body.
     */
    @Test
    void deflateBodyRequiringPresetDictionaryIsRejected() throws Exception {
        var deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
        deflater.setDictionary("dictionary".getBytes(UTF_8));

        assertDecodingFails("Deflate stream requires a preset dictionary.", deflate("payload", deflater));
    }

    /**
     * MEM-38 F-088: a deflate body used to be inflated completely before the stream was returned, so a
     * consumer reading through a size limit (e.g. the limit interceptor) could not stop the expansion.
     * The body is truncated at its very end; an eager decoder fails before handing out any byte.
     */
    @Test
    void deflateStreamIsDecodedLazily() throws Exception {
        final var content = randomText(64 * 1024);
        final var compressed = rawDeflate(content);
        final var truncated = Arrays.copyOf(compressed, compressed.length - 1);

        try (final var decoded = MessageUtil.getContentAsStream(deflateRequest(truncated))) {
            assertEquals(content.substring(0, 100), new String(decoded.readNBytes(100), UTF_8));
        }
    }

    /**
     * RFC 9110 8.4.1.3: a recipient SHOULD consider "x-gzip" to be equivalent to "gzip".
     */
    @Test
    void xGzipBodyIsDecodedLikeGzip() throws Exception {
        final var compressed = gzip("payload");
        for (String encoding : new String[]{"gzip", "x-gzip", "X-GZIP"}) {
            assertArrayEquals("payload".getBytes(UTF_8), MessageUtil.getContent(encodedRequest(compressed, encoding)), encoding);
            try (final var decoded = MessageUtil.getContentAsStream(encodedRequest(compressed, encoding))) {
                assertArrayEquals("payload".getBytes(UTF_8), decoded.readAllBytes(), encoding);
            }
        }
    }

    @Test
    void zstdBodyIsDecodedWhenSwitchedOn() throws Throwable {
        final var compressed = ZstdDecodingInputStreamTest.zstd("payload");
        withZstdDecoding(() -> {
            for (final var encoding : new String[]{"zstd", "ZSTD", ", zstd,"}) {
                assertArrayEquals("payload".getBytes(UTF_8), MessageUtil.getContent(encodedRequest(compressed, encoding)), encoding);
                try (final var decoded = MessageUtil.getContentAsStream(encodedRequest(compressed, encoding))) {
                    assertArrayEquals("payload".getBytes(UTF_8), decoded.readAllBytes(), encoding);
                }
            }
        });
    }

    /**
     * zstd decoding is off by default, so zstd is handled like a coding that Membrane does not decode.
     */
    @Test
    void zstdBodyIsNotDecodedByDefault() throws Exception {
        final var encoded = ZstdDecodingInputStreamTest.zstd("payload");
        assertArrayEquals(encoded, MessageUtil.getContent(encodedRequest(encoded, "zstd")));
        try (final var content = MessageUtil.getContentAsStream(encodedRequest(encoded, "zstd"))) {
            assertArrayEquals(encoded, content.readAllBytes());
        }
    }

    /**
     * Membrane decodes only a single coding, so zstd together with another coding is returned as it
     * is, whether both are on one field line or on two.
     */
    @Test
    void zstdWithAnotherCodingIsNotDecoded() throws Exception {
        final var encoded = ZstdDecodingInputStreamTest.zstd("payload");
        for (String[] fieldLines : new String[][]{{"gzip, zstd"}, {"gzip", "zstd"}, {"zstd", "gzip"}}) {
            final var description = String.join(" | ", fieldLines);
            assertArrayEquals(encoded, MessageUtil.getContent(encodedRequest(encoded, fieldLines)), description);
            try (final var content = MessageUtil.getContentAsStream(encodedRequest(encoded, fieldLines))) {
                assertArrayEquals(encoded, content.readAllBytes(), description);
            }
        }
    }

    /**
     * #3819: "Content-Encoding: gzip" plus "Content-Encoding: br" means "gzip, br" (RFC 9110 5.3).
     * Membrane does not decode several codings, so the body is returned as it is. Only reading the
     * first field line used to run the gzip decoder on Brotli bytes.
     */
    @Test
    void codingsOnSeveralFieldLinesAreNotDecoded() throws Exception {
        final var encoded = "brotli bytes".getBytes(UTF_8);
        assertArrayEquals(encoded, MessageUtil.getContent(encodedRequest(encoded, "gzip", "br")));
        try (final var content = MessageUtil.getContentAsStream(encodedRequest(encoded, "gzip", "br"))) {
            assertArrayEquals(encoded, content.readAllBytes());
        }
    }

    /**
     * RFC 9110 5.6.1: empty list elements do not count, so ", gzip," and an empty field line
     * followed by "gzip" both mean "gzip".
     */
    @Test
    void emptyListElementsAreIgnored() throws Exception {
        final var compressed = gzip("payload");
        for (String[] fieldLines : new String[][]{{", gzip,"}, {"", "gzip"}}) {
            final var description = String.join(" | ", fieldLines);
            assertArrayEquals("payload".getBytes(UTF_8), MessageUtil.getContent(encodedRequest(compressed, fieldLines)), description);
            try (final var decoded = MessageUtil.getContentAsStream(encodedRequest(compressed, fieldLines))) {
                assertArrayEquals("payload".getBytes(UTF_8), decoded.readAllBytes(), description);
            }
        }
    }

    /**
     * The decoder throws unchecked exceptions for malformed input; they must be reported as a
     * decoding failure like those of the other codings, so a request gets a 400.
     */
    @Test
    void corruptZstdBodyIsADecodingFailure() throws Throwable {
        final var corrupt = ZstdDecodingInputStreamTest.zstd("payload");
        corrupt[corrupt.length - 1] ^= 0x01; // content checksum
        withZstdDecoding(() -> {
            for (Executable decode : new Executable[]{
                    () -> MessageUtil.getContent(encodedRequest(corrupt, "zstd")),
                    () -> MessageUtil.getContentAsStream(encodedRequest(corrupt, "zstd")).readAllBytes()}) {
                final var e = assertThrows(ReadingBodyException.class, decode);
                assertEquals("zstd", assertInstanceOf(DecodingException.class, e.getCause()).getContentEncoding());
            }
        });
    }

    /**
     * The failure names the coding that was decoded, not the empty first field line.
     */
    @Test
    void decodingFailureNamesTheCodingNotTheFirstFieldLine() throws Exception {
        final var compressed = gzip("payload");
        final var truncated = Arrays.copyOf(compressed, compressed.length - 1);

        final var e = assertThrows(ReadingBodyException.class, () -> MessageUtil.getContent(encodedRequest(truncated, "", "gzip")));
        assertEquals("gzip", assertInstanceOf(DecodingException.class, e.getCause()).getContentEncoding());
    }

    /**
     * The zstd decoder needs sun.misc.Unsafe memory access, which later JDKs are to deny by default
     * (JEP 471). On such a JVM, zstd is handled like a coding that Membrane does not decode, even if
     * zstd decoding is switched on. The JVM option exists from JDK 23 on.
     */
    @Test
    void zstdIsNotDecodedOnAJvmThatDeniesUnsafe() throws Exception {
        assumeTrue(Runtime.version().feature() >= 23, "--sun-misc-unsafe-memory-access needs JDK 23 or later");
        final var process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--sun-misc-unsafe-memory-access=deny",
                "-D%s=true".formatted(ZstdDecodingInputStream.DECODING_PROPERTY),
                "-cp", System.getProperty("java.class.path"),
                ZstdOnAJvmThatDeniesUnsafe.class.getName())
                .redirectErrorStream(true)
                .start();
        try {
            final var output = assertTimeoutPreemptively(Duration.ofMinutes(1), () -> new String(process.getInputStream().readAllBytes(), UTF_8));
            assertEquals(0, process.waitFor(), output);
        } finally {
            process.destroyForcibly();
        }
    }

    /**
     * Runs in a JVM started with <code>--sun-misc-unsafe-memory-access=deny</code> and zstd decoding
     * switched on. The frame is fixed because the zstd encoder cannot run there either.
     */
    static final class ZstdOnAJvmThatDeniesUnsafe {
        public static void main(String[] args) throws Exception {
            final var encoded = ZstdDecodingInputStreamTest.REFERENCE_FRAME;
            assertArrayEquals(encoded, MessageUtil.getContent(encodedRequest(encoded, "zstd")));
            try (final var content = MessageUtil.getContentAsStream(encodedRequest(encoded, "zstd"))) {
                assertArrayEquals(encoded, content.readAllBytes());
            }
            assertFalse(encodedRequest(encoded, "zstd").isContentEncodingDecoded());
        }
    }

    private static void assertDecodesTo(String expected, byte[] compressed) throws Exception {
        assertArrayEquals(expected.getBytes(UTF_8), MessageUtil.getContent(deflateRequest(compressed)));
        try (final var decoded = MessageUtil.getContentAsStream(deflateRequest(compressed))) {
            assertArrayEquals(expected.getBytes(UTF_8), decoded.readAllBytes());
        }
    }

    private static void assertDecodingFails(String reason, byte[] compressed) throws Exception {
        for (Executable decode : new Executable[]{
                () -> MessageUtil.getContent(deflateRequest(compressed)),
                () -> MessageUtil.getContentAsStream(deflateRequest(compressed)).readAllBytes()}) {
            final var e = assertThrows(ReadingBodyException.class, decode);
            final var decoding = assertInstanceOf(DecodingException.class, e.getCause());
            assertEquals("deflate", decoding.getContentEncoding());
            assertEquals(reason, decoding.getCause().getMessage());
        }
    }

    private static Message deflateRequest(byte[] compressed) throws URISyntaxException {
        return Request.post("/").body(compressed).header("Content-Encoding", "deflate").build();
    }

    private static Message encodedRequest(byte[] compressed, String... contentEncodingFieldLines) throws URISyntaxException {
        final var builder = Request.post("/").body(compressed);
        for (final var fieldLine : contentEncodingFieldLines)
            builder.header("Content-Encoding", fieldLine);
        return builder.build();
    }

    private static byte[] gzip(String value) throws IOException {
        final var compressed = new ByteArrayOutputStream();
        try (final var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(value.getBytes(UTF_8));
        }
        return compressed.toByteArray();
    }

    /** Text the compressor cannot shrink much, so the compressed stream spans many input buffers. */
    private static String randomText(int length) {
        final var random = new Random(42);
        final var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            sb.append((char) ('a' + random.nextInt(26)));
        return sb.toString();
    }

    private static byte[] rawDeflate(String value) {
        return deflate(value, new Deflater(Deflater.DEFAULT_COMPRESSION, true));
    }

    private static byte[] zlibDeflate(String value) {
        return deflate(value, new Deflater(Deflater.DEFAULT_COMPRESSION, false));
    }

    private static byte[] deflate(String value, Deflater deflater) {
        try {
            deflater.setInput(value.getBytes(UTF_8));
            deflater.finish();
            final var compressed = new ByteArrayOutputStream();
            final var buffer = new byte[8192];
            while (!deflater.finished())
                compressed.write(buffer, 0, deflater.deflate(buffer));
            return compressed.toByteArray();
        } finally {
            deflater.end();
        }
    }
}
