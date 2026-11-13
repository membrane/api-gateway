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

import io.airlift.compress.zstd.ZstdOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class ZstdDecodingInputStreamTest {

    private static final byte[] MAGIC = {0x28, (byte) 0xb5, 0x2f, (byte) 0xfd};

    /**
     * Made with the reference implementation (zstd 1.5.7) from {@link #REFERENCE_CONTENT}:
     * <code>zstd --check -19</code>. A single-segment frame with a content checksum.
     */
    private static final byte[] REFERENCE_FRAME = {
            0x28, (byte) 0xb5, 0x2f, (byte) 0xfd, 0x24, 0x3a, (byte) 0xa5, 0x01, 0x00, (byte) 0xa4, 0x02, 0x7b,
            0x22, 0x6d, 0x65, 0x73, 0x73, 0x61, 0x67, 0x65, 0x22, 0x3a, 0x22, 0x31,
            0x2c, 0x32, 0x2c, 0x33, 0x20, 0x77, 0x69, 0x74, 0x68, 0x20, 0x7a, 0x73,
            0x74, 0x64, 0x21, 0x22, 0x2c, 0x22, 0x69, 0x74, 0x65, 0x6d, 0x73, 0x22,
            0x3a, 0x5b, 0x2c, 0x5d, 0x7d, 0x02, 0x00, 0x29, 0x26, (byte) 0xee, (byte) 0xd7, 0x7c,
            0x02, 0x6d, (byte) 0xa5, (byte) 0xf4, (byte) 0x84
    };
    private static final String REFERENCE_CONTENT = "{\"message\":\"1,2,3 with zstd!\",\"items\":[1,2,3,1,2,3,1,2,3]}";

    @Test
    void decodesFrameOfTheReferenceImplementation() throws Exception {
        assertEquals(REFERENCE_CONTENT, new String(decode(REFERENCE_FRAME), UTF_8));
    }

    @Test
    void decodesEmptySmallAndMultiBlockContent() throws Exception {
        for (final var content : new String[]{"", "payload", randomText(1024 * 1024)}) {
            assertEquals(content, new String(decode(zstd(content)), UTF_8));
        }
    }

    @Test
    void decodesConcatenatedFrames() throws Exception {
        assertEquals("firstsecond", new String(decode(concat(zstd("first"), zstd("second"))), UTF_8));
    }

    /**
     * RFC 8878 section 3.1.2: skippable frames are skipped.
     */
    @Test
    void skipsSkippableFrames() throws Exception {
        final var skippable = new byte[]{0x5f, 0x2a, 0x4d, 0x18, 3, 0, 0, 0, 1, 2, 3};
        assertEquals("firstsecond", new String(decode(concat(skippable, zstd("first"), skippable, zstd("second"), skippable)), UTF_8));
    }

    @Test
    void acceptsWindowOfEightMegabytes() throws Exception {
        // Window_Descriptor: exponent 13 (2^23), mantissa 0
        assertEquals("abc", new String(decode(frame(0x00, new byte[]{13 << 3}, rawBlock("abc"))), UTF_8));
        // Single segment, 4-byte Frame_Content_Size: 2^23
        assertEquals("abc", new String(decode(frame(0xa0, new byte[]{0, 0, (byte) 0x80, 0}, rawBlock("abc"))), UTF_8));
    }

    /**
     * Without the check, the decoder keeps a window of 1 GiB filled by RLE blocks of 4 bytes each.
     */
    @Test
    void rejectsWindowLargerThanEightMegabytes() {
        // Window_Descriptor: exponent 13, mantissa 1: 2^23 + 2^20
        assertWindowTooLarge(frame(0x00, new byte[]{13 << 3 | 1}, rleBlocks(100)));
        // Window_Descriptor: exponent 20 (1 GiB)
        assertWindowTooLarge(frame(0x00, new byte[]{(byte) (20 << 3)}, rleBlocks(100)));
        // Single segment, 4-byte Frame_Content_Size: 2^23 + 1
        assertWindowTooLarge(frame(0xa0, new byte[]{1, 0, (byte) 0x80, 0}, rleBlocks(100)));
        // Single segment, 8-byte Frame_Content_Size: 2^64 - 1
        assertWindowTooLarge(frame(0xe0, new byte[]{-1, -1, -1, -1, -1, -1, -1, -1}, rleBlocks(100)));
    }

    @Test
    void rejectsWindowTooLargeInLaterFrame() {
        assertWindowTooLarge(concat(zstd("first"), frame(0x00, new byte[]{(byte) (20 << 3)}, rleBlocks(100))));
    }

    @Test
    void rejectsTruncatedStream() {
        for (int length = 1; length < REFERENCE_FRAME.length; length++)
            assertThrows(IOException.class, decoding(Arrays.copyOf(REFERENCE_FRAME, length)), "length " + length);
    }

    @Test
    void rejectsEmptyStream() {
        assertThrows(IOException.class, decoding(new byte[0]));
    }

    @Test
    void rejectsBadChecksumAsIOException() {
        final var corrupt = REFERENCE_FRAME.clone();
        corrupt[corrupt.length - 1] ^= 0x01;
        assertThrows(IOException.class, decoding(corrupt));
    }

    @Test
    void rejectsCorruptBlockAsIOException() {
        final var compressed = zstd(randomText(64 * 1024));
        for (int i = 20; i < compressed.length; i += 97) {
            final var corrupt = compressed.clone();
            corrupt[i] ^= 0x55;
            assertThrows(IOException.class, decoding(corrupt), "byte " + i);
        }
    }

    @Test
    void rejectsInputThatIsNotZstd() {
        final var e = assertThrows(IOException.class, decoding("not zstd".getBytes(UTF_8)));
        assertEquals("Not a zstd frame.", e.getMessage());
    }

    @Test
    void rejectsReservedBlockType() {
        final var e = assertThrows(IOException.class, decoding(frame(0x00, new byte[]{0}, blockHeader(true, 3, 0))));
        assertEquals("Reserved zstd block type.", e.getMessage());
    }

    /**
     * The body is truncated at its very end; an eager decoder fails before handing out any byte.
     * The decoder hands out bytes once its window is full, so the frame has a 128 KiB window and
     * eight raw blocks of 100,000 bytes each.
     */
    @Test
    void decodesLazily() throws Exception {
        final var content = randomText(800_000);
        final var blocks = new ByteArrayOutputStream();
        for (int i = 0; i < 8; i++)
            blocks.writeBytes(concat(blockHeader(i == 7, 0, 100_000), content.substring(i * 100_000, (i + 1) * 100_000).getBytes(UTF_8)));
        final var compressed = frame(0x00, new byte[]{7 << 3}, blocks.toByteArray());
        try (final var decoded = new ZstdDecodingInputStream(new ByteArrayInputStream(Arrays.copyOf(compressed, compressed.length - 1)))) {
            assertEquals(content.substring(0, 100), new String(decoded.readNBytes(100), UTF_8));
        }
    }

    private static void assertWindowTooLarge(byte[] compressed) {
        final var e = assertThrows(IOException.class, decoding(compressed));
        assertTrue(e.getMessage().startsWith("zstd frame needs a window of "), e.getMessage());
    }

    private static org.junit.jupiter.api.function.Executable decoding(byte[] compressed) {
        return () -> decode(compressed);
    }

    private static byte[] decode(byte[] compressed) throws IOException {
        try (final var in = new ZstdDecodingInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        }
    }

    private static byte[] frame(int frameHeaderDescriptor, byte[] headerFields, byte[] blocks) {
        return concat(MAGIC, new byte[]{(byte) frameHeaderDescriptor}, headerFields, blocks);
    }

    private static byte[] rawBlock(String content) {
        final var bytes = content.getBytes(UTF_8);
        return concat(blockHeader(true, 0, bytes.length), bytes);
    }

    /** RLE blocks of 128 KiB each, 4 bytes of input per block. */
    private static byte[] rleBlocks(int count) {
        final var out = new ByteArrayOutputStream();
        for (int i = 0; i < count; i++) {
            out.writeBytes(blockHeader(i == count - 1, 1, 128 * 1024));
            out.write('x');
        }
        return out.toByteArray();
    }

    private static byte[] blockHeader(boolean last, int type, int size) {
        final int value = (last ? 1 : 0) | type << 1 | size << 3;
        return new byte[]{(byte) value, (byte) (value >>> 8), (byte) (value >>> 16)};
    }

    static byte[] zstd(String content) {
        final var out = new ByteArrayOutputStream();
        try (final var zstd = new ZstdOutputStream(out)) {
            zstd.write(content.getBytes(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        final var out = new ByteArrayOutputStream();
        for (final var part : parts)
            out.writeBytes(part);
        return out.toByteArray();
    }

    /** Text the compressor cannot shrink much, so the compressed stream spans many blocks. */
    private static String randomText(int length) {
        final var random = new Random(42);
        final var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            sb.append((char) ('a' + random.nextInt(26)));
        return sb.toString();
    }
}
