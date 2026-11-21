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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Decodes the "zstd" content coding (RFC 8878 frames, RFC 9659 content coding) while it is read.
 * <p>
 * The decoder keeps up to one window of decoded bytes per frame, and the frame header chooses
 * its size. RFC 9659 section 3 sets a limit of 8 MB on the window for this content coding. The decoder does
 * not check that limit for raw and RLE blocks, so a few bytes of input could make it hold a
 * window of gigabytes. The window size is therefore checked here, for every frame, before the
 * decoder reads the frame header.
 * <p>
 * The decoder hands out decoded bytes once its window buffer is full or the frame ends, and it may
 * decode one more block before that. So it reads ahead up to one window plus one block: at most
 * 8 MB plus 128 KB of decoded content is held per stream before a consumer, e.g. <code>limit</code>,
 * sees the first byte. The decoder does not check the block size either, so it is checked here too.
 * <p>
 * Nor does the decoder check that a frame's content has the Frame_Content_Size of its header.
 * The decoder therefore gets one frame at a time: the input seems to end after each frame, and
 * the decoded bytes are counted until the decoder reports that end. Then the next frame follows.
 * This relies on the decoder of aircompressor 2.0.3: at the end of its input after a complete frame it
 * returns -1, and it reads on when it is called again. One decoder is kept for all frames, so its
 * window buffer is reused, not allocated again for every frame.
 * <p>
 * The decoder throws unchecked exceptions for malformed input. They are rethrown as
 * {@link IOException}, so they are handled like a failure of the other content codings. So is a
 * decoder that cannot run on this JVM because the JVM denies sun.misc.Unsafe memory access.
 */
final class ZstdDecodingInputStream extends InputStream {

    /**
     * RFC 9659 section 3: 8 MB (2^23 bytes) for the zstd content coding, the limit browsers
     * configure for their decoders.
     */
    static final long MAX_WINDOW_SIZE = 1L << 23;

    private static final Logger log = LoggerFactory.getLogger(ZstdDecodingInputStream.class);

    private static final AtomicBoolean unavailableLogged = new AtomicBoolean();

    private final FrameChecker frames;
    private final InputStream decoded;

    private boolean started;
    private boolean ended;
    /** Decoded bytes of the current frame */
    private long frameContentRead;

    ZstdDecodingInputStream(InputStream compressed) {
        this(compressed, ZstdInputStream::new);
    }

    /**
     * @param decoder makes the decoding stream from the checked compressed bytes; tests replace the decoder with it
     */
    ZstdDecodingInputStream(InputStream compressed, Function<InputStream, InputStream> decoder) {
        frames = new FrameChecker(compressed);
        decoded = decoder.apply(frames);
    }

    @Override
    public int read() throws IOException {
        final var b = new byte[1];
        return read(b, 0, 1) == -1 ? -1 : b[0] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            return readFrames(b, off, len);
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        } catch (LinkageError e) {
            throw decoderUnavailable(e);
        }
    }

    private int readFrames(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0)
            return 0;
        if (ended)
            return -1;
        if (!started) {
            started = true;
            if (!frames.nextFrame()) {
                // RFC 8878 section 3.1.2: skippable frames only are an empty content; no frame at all is not zstd.
                if (!frames.skippedFrame)
                    throw new IOException("No zstd frame.");
                ended = true;
                return -1;
            }
        }
        while (true) {
            final var n = decoded.read(b, off, len);
            if (n != -1) {
                frameContentRead += n;
                if (frames.contentSize != FrameChecker.UNKNOWN_CONTENT_SIZE && Long.compareUnsigned(frameContentRead, frames.contentSize) > 0)
                    throw contentSizeMismatch();
                return n;
            }
            // The decoder has passed on the whole frame.
            if (frames.contentSize != FrameChecker.UNKNOWN_CONTENT_SIZE && frameContentRead != frames.contentSize)
                throw contentSizeMismatch();
            if (!frames.nextFrame()) {
                ended = true;
                return -1;
            }
            frameContentRead = 0;
        }
    }

    private IOException contentSizeMismatch() {
        return new IOException("zstd frame content does not have the Frame_Content_Size of %s bytes in its header."
                .formatted(Long.toUnsignedString(frames.contentSize)));
    }

    /**
     * The decoder uses sun.misc.Unsafe. On a JVM that denies its memory access methods, the decoder's
     * class initialization fails on the first read (ExceptionInInitializerError) and every later read
     * fails with NoClassDefFoundError. Every zstd body then fails, so the cause is logged once as an error.
     */
    private static IOException decoderUnavailable(LinkageError e) {
        final var message = "The zstd decoder cannot run on this JVM: it needs sun.misc.Unsafe memory access (see --sun-misc-unsafe-memory-access).";
        if (unavailableLogged.compareAndSet(false, true))
            log.error(message, e);
        return new IOException(message, e);
    }

    @Override
    public void close() throws IOException {
        decoded.close();
    }

    /**
     * Walks the frame structure of the compressed bytes on their way to the decoder. It reads each
     * frame header and block header, and forwards block contents without parsing them. It rejects a
     * frame whose window is larger than {@link #MAX_WINDOW_SIZE}, and it drops skippable frames,
     * which the decoder does not accept, although RFC 8878 section 3.1.2 says to skip them.
     * <p>
     * Its input ends after each frame until {@link #nextFrame()} starts the next one.
     */
    private static final class FrameChecker extends InputStream {

        /**
         * No Frame_Content_Size field. The reference implementation also takes an 8-byte
         * Frame_Content_Size of 2^64 - 1 as unknown (ZSTD_CONTENTSIZE_UNKNOWN).
         */
        static final long UNKNOWN_CONTENT_SIZE = -1;

        private static final int FRAME_MAGIC = 0xFD2FB528;
        private static final int SKIPPABLE_FRAME_MAGIC = 0x184D2A50;
        private static final int SKIPPABLE_FRAME_MAGIC_MASK = 0xFFFFFFF0;
        /** RFC 8878 section 3.1.1.2: a block is at most 128 KB, and never larger than the window. */
        private static final long MAX_BLOCK_SIZE = 128 * 1024;

        private final InputStream in;

        /** Header bytes already checked and not yet passed to the decoder. */
        private byte[] header = new byte[0];
        private int headerPosition;
        /** Bytes of block content or checksum to forward unparsed before the next header. */
        private long passThrough;
        /** Whether the next header is a block header rather than the start of a frame. */
        private boolean inFrame;
        private boolean contentChecksum;
        /** Block_Maximum_Size of the current frame. */
        private long blockMaximumSize;
        /** Frame_Content_Size of the current frame, unsigned, or {@link #UNKNOWN_CONTENT_SIZE}. */
        long contentSize;
        /** Whether a skippable frame has been dropped. */
        boolean skippedFrame;

        FrameChecker(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            final var b = new byte[1];
            return read(b, 0, 1) == -1 ? -1 : b[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0)
                return 0;
            while (true) {
                if (headerPosition < header.length) {
                    final int n = Math.min(len, header.length - headerPosition);
                    System.arraycopy(header, headerPosition, b, off, n);
                    headerPosition += n;
                    return n;
                }
                if (passThrough > 0) {
                    final int n = in.read(b, off, (int) Math.min(len, passThrough));
                    if (n == -1)
                        throw truncated();
                    passThrough -= n;
                    return n;
                }
                if (!inFrame)
                    return -1;
                readBlockHeader();
            }
        }

        /**
         * Starts the next zstd frame, after the current one has been read to its end. Skippable
         * frames before it are dropped.
         *
         * @return false if the input ends cleanly instead
         */
        boolean nextFrame() throws IOException {
            while (true) {
                final var magicBytes = in.readNBytes(4);
                if (magicBytes.length == 0)
                    return false;
                if (magicBytes.length < 4)
                    throw truncated();
                final int magic = (int) littleEndian(magicBytes, 0, 4);
                if ((magic & SKIPPABLE_FRAME_MAGIC_MASK) == SKIPPABLE_FRAME_MAGIC) {
                    skipFrameContent(littleEndian(readFully(4), 0, 4));
                    skippedFrame = true;
                    continue;
                }
                if (magic != FRAME_MAGIC)
                    throw new IOException("Not a zstd frame.");
                readFrameHeader(magicBytes);
                return true;
            }
        }

        /**
         * RFC 8878 section 3.1.1.1
         */
        private void readFrameHeader(byte[] magicBytes) throws IOException {
            final int descriptor = readFully(1)[0] & 0xff;
            // RFC 8878 section 3.1.1.1.1: a decoder must reject a set Reserved_Bit. The decoder ignores it.
            if ((descriptor & 0x08) != 0)
                throw new IOException("Reserved bit set in zstd frame header.");
            final boolean singleSegment = (descriptor & 0x20) != 0;
            final int dictionaryIdSize = switch (descriptor & 0x03) {
                case 0 -> 0;
                case 1 -> 1;
                case 2 -> 2;
                default -> 4;
            };
            final int contentSizeSize = switch (descriptor >>> 6) {
                case 0 -> singleSegment ? 1 : 0;
                case 1 -> 2;
                case 2 -> 4;
                default -> 8;
            };
            final int windowDescriptorSize = singleSegment ? 0 : 1;
            final var rest = readFully(windowDescriptorSize + dictionaryIdSize + contentSizeSize);

            contentSize = contentSizeSize == 0 ? UNKNOWN_CONTENT_SIZE : frameContentSize(rest, windowDescriptorSize + dictionaryIdSize, contentSizeSize);
            // RFC 8878 section 3.1.1.1.4: in a single-segment frame, the window size is the content size.
            final long windowSize = singleSegment ? contentSize : windowSize(rest[0] & 0xff);
            // Negative: a Frame_Content_Size of 2^63 or more
            if (windowSize < 0 || windowSize > MAX_WINDOW_SIZE)
                throw new IOException("zstd frame needs a window of %s bytes, more than the %d bytes allowed for the zstd content coding."
                        .formatted(Long.toUnsignedString(windowSize), MAX_WINDOW_SIZE));

            blockMaximumSize = Math.min(windowSize, MAX_BLOCK_SIZE);
            contentChecksum = (descriptor & 0x04) != 0;
            inFrame = true;
            header = forwardedFrameHeader(magicBytes, descriptor, rest, windowDescriptorSize, dictionaryIdSize);
            headerPosition = 0;
        }

        /**
         * The frame header as the decoder gets it. RFC 8878 section 3.1.1.1.3: a Dictionary_ID of 0
         * means the same as no Dictionary_ID field. The decoder rejects every Dictionary_ID field, so
         * a zero one is left out. Any other Dictionary_ID is forwarded, and the decoder rejects it.
         */
        private static byte[] forwardedFrameHeader(byte[] magicBytes, int descriptor, byte[] fields, int dictionaryIdOffset, int dictionaryIdSize) {
            final var out = new ByteArrayOutputStream(magicBytes.length + 1 + fields.length);
            out.writeBytes(magicBytes);
            if (dictionaryIdSize == 0 || littleEndian(fields, dictionaryIdOffset, dictionaryIdSize) != 0) {
                out.write(descriptor);
                out.writeBytes(fields);
                return out.toByteArray();
            }
            out.write(descriptor & ~0x03); // Dictionary_ID_Flag: no field
            out.write(fields, 0, dictionaryIdOffset);
            final int afterDictionaryId = dictionaryIdOffset + dictionaryIdSize;
            out.write(fields, afterDictionaryId, fields.length - afterDictionaryId);
            return out.toByteArray();
        }

        /**
         * RFC 8878 section 3.1.1.1.2
         */
        private static long windowSize(int windowDescriptor) {
            final long windowBase = 1L << (10 + (windowDescriptor >>> 3));
            return windowBase + (windowBase / 8) * (windowDescriptor & 0x07);
        }

        /**
         * RFC 8878 section 3.1.1.1.4
         */
        private static long frameContentSize(byte[] bytes, int offset, int size) {
            final long value = littleEndian(bytes, offset, size);
            return size == 2 ? value + 256 : value;
        }

        /**
         * RFC 8878 section 3.1.1.2
         */
        private void readBlockHeader() throws IOException {
            final var blockHeader = readFully(3);
            final int value = (int) littleEndian(blockHeader, 0, 3);
            final boolean lastBlock = (value & 0x01) != 0;
            final int blockSize = value >>> 3;
            passThrough = switch ((value >>> 1) & 0x03) {
                case 0, 2 -> blockSize; // Raw, Compressed
                case 1 -> 1;            // RLE: the byte to repeat
                default -> throw new IOException("Reserved zstd block type.");
            };
            if (blockSize > blockMaximumSize)
                throw new IOException("zstd block of %d bytes is larger than the %d bytes allowed in this frame."
                        .formatted(blockSize, blockMaximumSize));
            if (lastBlock) {
                if (contentChecksum)
                    passThrough += 4;
                inFrame = false;
            }
            header = blockHeader;
            headerPosition = 0;
        }

        private byte[] readFully(int length) throws IOException {
            final var bytes = in.readNBytes(length);
            if (bytes.length < length)
                throw truncated();
            return bytes;
        }

        private void skipFrameContent(long length) throws IOException {
            try {
                in.skipNBytes(length);
            } catch (EOFException e) {
                throw truncated();
            }
        }

        private static long littleEndian(byte[] bytes, int offset, int length) {
            long value = 0;
            for (int i = length - 1; i >= 0; i--)
                value = (value << 8) | (bytes[offset + i] & 0xff);
            return value;
        }

        private static IOException truncated() {
            return new IOException("Truncated zstd stream.");
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
