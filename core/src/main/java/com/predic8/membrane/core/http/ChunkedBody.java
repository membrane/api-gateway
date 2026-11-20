/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.http;

import com.predic8.membrane.core.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.http.ChunkedBodyTransferer.ZERO;
import static java.lang.Long.toHexString;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Reads the body with "Transfer-Encoding: chunked".
 * <p>
 * See {@link ChunkedBodyTransferer} for writing a body using chunks.
 * <p>
 * This class internally reads body chunks piece by piece. When accessing the body using streams, not all chunks
 * might therefore be read immediately.
 * <p>
 * The framing is decoded by {@link ChunkedDecoder}; this class decides whether the chunks are retained,
 * forwarded or dropped.
 */
public class ChunkedBody extends AbstractBody {

    private static final Logger log = LoggerFactory.getLogger(ChunkedBody.class.getName());

    private final ChunkedDecoder decoder;
    private long lengthStreamed;
    private Header trailer;

    public ChunkedBody(InputStream in) {
        decoder = new ChunkedDecoder(in);
    }

    @Override
    public void read() {
        throwIfFailed(); // before the drain below: it would re-read the dead stream
        try {
            if (lazyReadStarted && !isRead())
                ByteUtil.readStream(getContentAsStream());
            lazyReadStarted = true;
            super.read();
        } catch (IOException e) {
            throw fail(e);
        }
    }

    @Override
    public void write(AbstractBodyTransferer out, boolean retainCopy) {
        throwIfFailed(); // before the drain below: it would re-read the dead stream
        try {
            if (lazyReadStarted && !isRead())
                ByteUtil.readStream(getContentAsStream());
        } catch (IOException e) {
            throw fail(e);
        }
        super.write(out, retainCopy);
    }

    /**
     * Continues a read started through {@link #getContentAsStream()}: the chunks it already holds belong
     * to the body and count towards {@code maxLength}.
     */
    @Override
    public BodyLengthCheck readUpTo(long maxLength) {
        throwIfFailed(); // before reading on: the stream is dead
        if (!lazyReadStarted || isRead()) {
            lazyReadStarted = true;
            return super.readUpTo(maxLength);
        }
        requireContentRetained();
        return readRest(maxLength);
    }

    /**
     * Reads no further than the size of the chunk that would take the body beyond {@code maxLength}; that
     * chunk is neither allocated nor read. The chunks a partial read through {@link #getContentAsStream()}
     * already holds count towards it.
     */
    @Override
    protected boolean readChunksWithin(long maxLength) throws IOException {
        // the observers get the chunks only once the whole body has been read
        var length = getLengthOfChunks();
        if (length > maxLength)
            return false;
        final var chunkList = new ArrayList<Chunk>();
        try {
            Chunk chunk;
            while ((chunk = decoder.next(maxLength - length)) != null) {
                chunkList.add(chunk);
                length += chunk.getLength();
            }
        } catch (ChunkedDecoder.ChunkTooLongException e) {
            return false;
        }
        chunks.addAll(chunkList);
        trailer = decoder.getTrailer();
        for (Chunk c : chunkList)
            notifyChunk(c);
        return true;
    }

    /**
     * @return <code>true</code>: a discarded body is not retained
     */
    @Override
    protected boolean dropsContentOnDiscard() {
        return true;
    }

    @Override
    protected DropOutcome dropContent() throws IOException {
        Chunk chunk;
        while ((chunk = decoder.next()) != null)
            notifyChunk(chunk);
        trailer = decoder.getTrailer();
        return DropOutcome.DRAINED;
    }

    /**
     * Whether reading has started: the observers got <code>bodyRequested</code> and {@link #chunks} may
     * hold the first part of the body, which a later read must continue instead of starting over.
     */
    private boolean lazyReadStarted = false;

    public InputStream getContentAsStream() {
        throwIfFailed(); // before chunks.clear() below, which would drop the partially read body
        requireContentRetained();
        if (!lazyReadStarted) {
            lazyReadStarted = true;
            for (MessageObserver observer : observers)
                observer.bodyRequested(this);
            chunks.clear();
        }

        return new BodyInputStream(chunks) {
            @Override
            protected Chunk readNextChunk() throws IOException {
                if (isRead())
                    return null;
                throwIfFailed();
                try {
                    final var chunk = decoder.next();
                    if (chunk != null) {
                        notifyChunk(chunk);
                        return chunk;
                    }
                    trailer = decoder.getTrailer();

                    // After the trailer(0 + CRLF + CRLF) is read we mark the body read
                    markAsRead();

                    return null;
                } catch (IOException e) {
                    // Record the failure (and notify the observers), but keep the InputStream contract:
                    // callers of read() expect an IOException, not a ReadingBodyException.
                    fail(e);
                    throw e;
                }
            }
        };
    }

    @Override
    protected void writeStreamed(AbstractBodyTransferer out) {
        log.debug("writeStreamed");
        try {
            Chunk chunk;
            while ((chunk = decoder.next()) != null) {
                try {
                    out.write(chunk);
                } catch (IOException e) {
                    throw new WritingBodyException(e);
                }
                notifyChunk(chunk);
                lengthStreamed += chunk.getLength();
            }
            trailer = decoder.getTrailer();
        } catch (IOException e) { // note that we only want to catch the IOExceptions associated to *reading* the body
            throw fail(e);
        }
        try {
            out.finish(trailer);
        } catch (IOException e) {
            throw new WritingBodyException(e);
        }
        markAsRead();
    }

    private void notifyChunk(Chunk chunk) {
        for (MessageObserver observer : observers)
            observer.bodyChunk(chunk);
    }

    protected int getRawLength() {
        long length = getLength();
        for (Chunk chunk : chunks) {
            length += toHexString(chunk.getLength()).getBytes(UTF_8).length;
            length += 2L * CRLF_BYTES.length;
        }
        length += "0".getBytes(UTF_8).length;
        length += 2L * CRLF_BYTES.length;
        if (length > MAX_ARRAY_LENGTH)
            throw new BodyTooLargeException("Chunked message body of " + length + " bytes is too large to load into memory (limit " + MAX_ARRAY_LENGTH + " bytes). Stream the message instead.");
        return (int) length;
    }

    @Override
    protected byte[] getRawLocal() {
        byte[] raw = new byte[getRawLength()];
        int destPos = 0;
        for (Chunk chunk : chunks) {
            destPos = chunk.copyChunkLength(raw, destPos);
            destPos = copyCRLF(raw, destPos);
            destPos = chunk.copyChunk(raw, destPos);
            destPos = copyCRLF(raw, destPos);
        }
        destPos = copyLastChunk(raw, destPos);
        copyCRLF(raw, destPos);
        return raw;
    }

    private int copyLastChunk(byte[] raw, int destPos) {
        System.arraycopy(ZERO, 0, raw, destPos, ZERO.length);
        destPos += ZERO.length;
        destPos = copyCRLF(raw, destPos);
        return destPos;
    }

    private int copyCRLF(byte[] raw, int destPos) {
        System.arraycopy(CRLF_BYTES, 0, raw, destPos, 2);
        return destPos + 2;
    }

    @Override
    protected void writeAlreadyRead(AbstractBodyTransferer out) throws IOException {
        if (getLength() > 0)
            for (Chunk chunk : chunks) {
                out.write(chunk);
            }
        out.finish(trailer);
    }

    @Override
    public long getLength() {
        if (wasStreamed())
            return lengthStreamed;
        return super.getLength();
    }

    @Override
    public boolean hasTrailer() {
        return trailer != null;
    }

    @Override
    public Header getTrailer() {
        return trailer;
    }

    @Override
    public boolean setTrailer(Header trailer) {
        this.trailer = trailer;
        return true;
    }
}
