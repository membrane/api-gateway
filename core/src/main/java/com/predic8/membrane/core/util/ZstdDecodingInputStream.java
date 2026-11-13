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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Decodes the "zstd" content coding (RFC 8878 frames, RFC 9659 content coding) while it is read.
 * <p>
 * The decoder keeps up to one window of decoded bytes per frame, and the frame header chooses
 * its size. RFC 9659 section 3 sets a limit of 8 MB on the window for this content coding. The decoder does
 * not check that limit for raw and RLE blocks, so a few bytes of input could make it hold a
 * window of gigabytes. The window size is therefore checked here, for every frame, before the
 * decoder reads the frame header.
 * <p>
 * The decoder hands out decoded bytes once its window buffer is full or the frame ends, so it
 * reads up to one window of input ahead: at most 8 MB of decoded content is held per stream
 * before a consumer, e.g. <code>limit</code>, sees the first byte.
 * <p>
 * The decoder throws unchecked exceptions for malformed input. They are rethrown as
 * {@link IOException}, so they are handled like a failure of the other content codings.
 */
final class ZstdDecodingInputStream extends InputStream {

    /**
     * RFC 9659 section 3: 8 MB (2^23 bytes) for the zstd content coding, the limit browsers
     * configure for their decoders.
     */
    static final long MAX_WINDOW_SIZE = 1L << 23;

    private final InputStream decoded;

    ZstdDecodingInputStream(InputStream compressed) {
        decoded = new ZstdInputStream(new FrameChecker(compressed));
    }

    @Override
    public int read() throws IOException {
        try {
            return decoded.read();
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            return decoded.read(b, off, len);
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }
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
     */
    private static final class FrameChecker extends InputStream {

        private static final int FRAME_MAGIC = 0xFD2FB528;
        private static final int SKIPPABLE_FRAME_MAGIC = 0x184D2A50;
        private static final int SKIPPABLE_FRAME_MAGIC_MASK = 0xFFFFFFF0;

        private final InputStream in;

        /** Header bytes already checked and not yet passed to the decoder. */
        private byte[] header = new byte[0];
        private int headerPosition;
        /** Bytes of block content or checksum to forward unparsed before the next header. */
        private long passThrough;
        /** Whether the next header is a block header rather than the start of a frame. */
        private boolean inFrame;
        private boolean contentChecksum;

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
                if (inFrame)
                    readBlockHeader();
                else if (!readFrameStart())
                    return -1;
            }
        }

        /**
         * @return false if the input ends cleanly between two frames
         */
        private boolean readFrameStart() throws IOException {
            final var magicBytes = in.readNBytes(4);
            if (magicBytes.length == 0)
                return false;
            if (magicBytes.length < 4)
                throw truncated();
            final int magic = (int) littleEndian(magicBytes, 0, 4);
            if ((magic & SKIPPABLE_FRAME_MAGIC_MASK) == SKIPPABLE_FRAME_MAGIC) {
                skipFrameContent(littleEndian(readFully(4), 0, 4));
                return true;
            }
            if (magic != FRAME_MAGIC)
                throw new IOException("Not a zstd frame.");
            readFrameHeader(magicBytes);
            return true;
        }

        /**
         * RFC 8878 section 3.1.1.1
         */
        private void readFrameHeader(byte[] magicBytes) throws IOException {
            final int descriptor = readFully(1)[0] & 0xff;
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

            final long windowSize = singleSegment
                    ? frameContentSize(rest, dictionaryIdSize, contentSizeSize)
                    : windowSize(rest[0] & 0xff);
            // Negative: a Frame_Content_Size of 2^63 or more
            if (windowSize < 0 || windowSize > MAX_WINDOW_SIZE)
                throw new IOException("zstd frame needs a window of %s bytes, more than the %d bytes allowed for the zstd content coding."
                        .formatted(Long.toUnsignedString(windowSize), MAX_WINDOW_SIZE));

            contentChecksum = (descriptor & 0x04) != 0;
            inFrame = true;
            header = new byte[magicBytes.length + 1 + rest.length];
            System.arraycopy(magicBytes, 0, header, 0, magicBytes.length);
            header[magicBytes.length] = (byte) descriptor;
            System.arraycopy(rest, 0, header, magicBytes.length + 1, rest.length);
            headerPosition = 0;
        }

        /**
         * RFC 8878 section 3.1.1.1.2
         */
        private static long windowSize(int windowDescriptor) {
            final long windowBase = 1L << (10 + (windowDescriptor >>> 3));
            return windowBase + (windowBase / 8) * (windowDescriptor & 0x07);
        }

        /**
         * RFC 8878 section 3.1.1.1.4: in a single-segment frame, the window size is the content size.
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
