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

import io.airlift.compress.zstd.ZstdInputStream;
import io.airlift.compress.zstd.ZstdOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

import static com.predic8.membrane.core.util.ZstdTestUtil.withZstdDecoding;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class ZstdDecodingInputStreamTest {

    private static final byte[] MAGIC = {0x28, (byte) 0xb5, 0x2f, (byte) 0xfd};

    /**
     * Made with the reference implementation (zstd 1.5.7) from {@link #REFERENCE_CONTENT}:
     * <code>zstd --check -19</code>. A single-segment frame with a content checksum.
     */
    static final byte[] REFERENCE_FRAME = {
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
        // Single segment, 4-byte Frame_Content_Size: 2^23, filled by 64 RLE blocks of 128 KiB
        final var content = new byte[1 << 23];
        Arrays.fill(content, (byte) 'x');
        assertArrayEquals(content, decode(frame(0xa0, new byte[]{0, 0, (byte) 0x80, 0}, rleBlocks(64))));
    }

    /**
     * RFC 8878 section 3.1.1.1.1: a decoder must reject a frame whose Reserved_Bit is set.
     */
    @Test
    void rejectsReservedBitInFrameHeader() {
        final var e = assertThrows(IOException.class, decoding(frame(0x08, new byte[]{0}, rawBlock("abc"))));
        assertEquals("Reserved bit set in zstd frame header.", e.getMessage());
    }

    /**
     * RFC 8878 section 3.1.1.2: Block_Maximum_Size is the smaller of the window size and 128 KiB.
     */
    @Test
    void acceptsBlocksOfMaximumSize() throws Exception {
        // Window_Descriptor 0: 1 KiB window
        assertEquals(1024, decode(frame(0x00, new byte[]{0}, concat(blockHeader(true, 0, 1024), new byte[1024]))).length);
        // 8 MiB window: 128 KiB
        assertEquals(128 * 1024, decode(frame(0x00, new byte[]{13 << 3}, rleBlocks(1))).length);
        // Single segment, Frame_Content_Size 3
        assertEquals("abc", new String(decode(frame(0x20, new byte[]{3}, rawBlock("abc"))), UTF_8));
    }

    @Test
    void rejectsBlocksLargerThanMaximumSize() {
        assertBlockTooLarge(frame(0x00, new byte[]{0}, concat(blockHeader(true, 0, 1025), new byte[1025])));
        assertBlockTooLarge(frame(0x00, new byte[]{13 << 3}, concat(blockHeader(true, 1, 128 * 1024 + 1), new byte[]{'x'})));
        assertBlockTooLarge(frame(0x20, new byte[]{3}, rawBlock("abcd")));
    }

    private static void assertBlockTooLarge(byte[] compressed) {
        final var e = assertThrows(IOException.class, decoding(compressed));
        assertTrue(e.getMessage().startsWith("zstd block of "), e.getMessage());
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

    /**
     * RFC 8878 section 3.1.1.1.3: a Dictionary_ID of 0 means the same as no Dictionary_ID field.
     */
    @Test
    void acceptsZeroDictionaryId() throws Exception {
        // Window_Descriptor 0 (1 KiB), then a zero Dictionary_ID of 1, 2 and 4 bytes
        assertEquals("abc", new String(decode(frame(0x01, new byte[]{0, 0}, rawBlock("abc"))), UTF_8));
        assertEquals("abc", new String(decode(frame(0x02, new byte[]{0, 0, 0}, rawBlock("abc"))), UTF_8));
        assertEquals("abc", new String(decode(frame(0x03, new byte[]{0, 0, 0, 0, 0}, rawBlock("abc"))), UTF_8));
        // Single segment: a zero 1-byte Dictionary_ID, then a 1-byte Frame_Content_Size of 3
        assertEquals("abc", new String(decode(frame(0x21, new byte[]{0, 3}, rawBlock("abc"))), UTF_8));
    }

    @Test
    void rejectsDictionaryOtherThanZero() {
        assertThrows(IOException.class, decoding(frame(0x01, new byte[]{0, 7}, rawBlock("abc"))));
        assertThrows(IOException.class, decoding(frame(0x23, new byte[]{0, 0, 0, 1, 3}, rawBlock("abc"))));
    }

    /**
     * RFC 8878 section 3.1.1.1.4: the decoded content must have the Frame_Content_Size of the
     * frame header. The reference implementation rejects a mismatch as data corruption.
     */
    @Test
    void rejectsContentShorterThanFrameContentSize() {
        // Single segment, Frame_Content_Size 4
        assertContentSizeMismatch(frame(0x20, new byte[]{4}, rawBlock("abc")));
        // 2-byte Frame_Content_Size 0 (256 bytes), 255 bytes in a 1 KiB window
        assertContentSizeMismatch(frame(0x40, new byte[]{0, 0, 0}, rleBlock(true, 255)));
    }

    @Test
    void rejectsContentLongerThanFrameContentSize() {
        // Single segment, Frame_Content_Size 3, two raw blocks of 2 bytes each
        assertContentSizeMismatch(frame(0x20, new byte[]{3}, concat(blockHeader(false, 0, 2), "ab".getBytes(UTF_8), rawBlock("cd"))));
        // 2-byte Frame_Content_Size 0 (256 bytes), 257 bytes in a 1 KiB window
        assertContentSizeMismatch(frame(0x40, new byte[]{0, 0, 0}, rleBlock(true, 257)));
    }

    @Test
    void rejectsContentSizeMismatchInLaterFrame() {
        assertContentSizeMismatch(concat(zstd("first"), frame(0x20, new byte[]{4}, rawBlock("abc"))));
        assertContentSizeMismatch(concat(REFERENCE_FRAME, frame(0x20, new byte[]{3}, rawBlock("ab"))));
    }

    @Test
    void acceptsContentOfFrameContentSize() throws Exception {
        // 2-byte Frame_Content_Size 0 (256 bytes)
        assertEquals(256, decode(frame(0x40, new byte[]{0, 0, 0}, rleBlock(true, 256))).length);
        // Concatenated frames, each with a Frame_Content_Size
        assertEquals(REFERENCE_CONTENT + "abc", new String(decode(concat(REFERENCE_FRAME, frame(0x20, new byte[]{3}, rawBlock("abc")))), UTF_8));
    }

    /**
     * The reference implementation takes an 8-byte Frame_Content_Size of 2^64 - 1 as unknown
     * (ZSTD_CONTENTSIZE_UNKNOWN), so does not check it.
     */
    @Test
    void acceptsLargestFrameContentSizeAsUnknown() throws Exception {
        assertEquals("abc", new String(decode(frame(0xc0, new byte[]{0, -1, -1, -1, -1, -1, -1, -1, -1}, rawBlock("abc"))), UTF_8));
    }

    /**
     * RFC 8878 section 3.1.2: skippable frames only are an empty content, as in the reference implementation.
     */
    @Test
    void decodesSkippableFramesOnlyToEmptyContent() throws Exception {
        final var skippable = new byte[]{0x5f, 0x2a, 0x4d, 0x18, 3, 0, 0, 0, 1, 2, 3};
        try (final var in = new ZstdDecodingInputStream(new ByteArrayInputStream(concat(skippable, skippable)))) {
            assertEquals(-1, in.read());
            assertEquals(-1, in.read(new byte[10], 0, 10));
        }
    }

    private static void assertContentSizeMismatch(byte[] compressed) {
        final var e = assertThrows(IOException.class, decoding(compressed));
        assertTrue(e.getMessage().startsWith("zstd frame content does not have the Frame_Content_Size "), e.getMessage());
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
        final var e = assertThrows(IOException.class, decoding(new byte[0]));
        assertEquals("No zstd frame.", e.getMessage());
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

    /**
     * A JVM that denies sun.misc.Unsafe memory access (<code>--sun-misc-unsafe-memory-access=deny</code>)
     * makes the decoder fail its class initialization: the first read throws
     * ExceptionInInitializerError, every later one NoClassDefFoundError.
     */
    @Test
    void decoderThatCannotRunIsNotAvailable() {
        for (final var error : new Error[]{
                new ExceptionInInitializerError(new UnsupportedOperationException("objectFieldOffset")),
                new NoClassDefFoundError("Could not initialize class io.airlift.compress.zstd.UnsafeUtil")})
            assertFalse(ZstdDecodingInputStream.decoderWorks(compressed -> failingStream(error)), error.toString());
    }

    @Test
    void decoderWorksOnThisJvm() {
        assertTrue(ZstdDecodingInputStream.decoderWorks(ZstdInputStream::new));
    }

    @Test
    void decodingIsEnabledOnlyWhenSwitchedOn() throws Throwable {
        assertFalse(ZstdDecodingInputStream.isDecodingEnabled());
        withZstdDecoding(() -> assertTrue(ZstdDecodingInputStream.isDecodingEnabled()));
        assertFalse(ZstdDecodingInputStream.isDecodingEnabled());
    }

    private static InputStream failingStream(Error error) {
        return new InputStream() {
            @Override
            public int read() {
                throw error;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                throw error;
            }
        };
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

    private static byte[] rleBlock(boolean last, int size) {
        return concat(blockHeader(last, 1, size), new byte[]{'x'});
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
