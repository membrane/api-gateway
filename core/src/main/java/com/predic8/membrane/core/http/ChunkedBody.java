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

import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.ChunkedBodyTransferrer.*;
import static java.lang.Long.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * Reads the body with "Transfer-Encoding: chunked".
 * <p>
 * See {@link ChunkedBodyTransferrer} for writing a body using chunks.
 */
public class ChunkedBody extends AbstractBody {

    private static final Logger log = LoggerFactory.getLogger(ChunkedBody.class.getName());

    private final InputStream inputStream;
    private long lengthStreamed;
    private Header trailer;

    public ChunkedBody(InputStream in) {
        log.debug("ChunkedInOutBody constructor");
        inputStream = in;
    }

    private static List<Chunk> readChunks(InputStream in) throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        int chunkSize;
        while ((chunkSize = readChunkSize(in)) > 0) {
            chunks.add(new Chunk(ByteUtil.readByteArray(in, chunkSize)));
            //noinspection ResultOfMethodCallIgnored
            in.read(); // CR
            //noinspection ResultOfMethodCallIgnored
            in.read(); // LF
        }
        return chunks;
    }

    private static Header readTrailer(InputStream in) throws IOException {
        in.mark(2);
        if (in.read() == 13) {
            //noinspection ResultOfMethodCallIgnored
            in.read();
            return null;
        }
        in.reset();
        try {
            return new Header(in);
        } catch (EndOfStreamException e) {
            throw new IOException(e);
        }
    }

    private static void readChunksAndDrop(InputStream in, List<MessageObserver> observers) throws IOException {
        int chunkSize;
        while ((chunkSize = readChunkSize(in)) > 0) {
            Chunk chunk = new Chunk(ByteUtil.readByteArray(in, chunkSize));
            for (MessageObserver observer : observers)
                observer.bodyChunk(chunk);
            //noinspection ResultOfMethodCallIgnored
            in.read(); // CR
			//noinspection ResultOfMethodCallIgnored
            in.read(); // LF
        }
    }

    public static int readChunkSize(InputStream in) throws IOException {
        StringBuilder buffer = new StringBuilder();

        int c;
        while ((c = in.read()) != -1) {
            if (c == 13) {
                //noinspection ResultOfMethodCallIgnored
                in.read(); // LF
                break;
            }

            // ignore chunk extensions
            if (c == ';') {
                //noinspection StatementWithEmptyBody
                while ((c = in.read()) != 10)
                    ;
            }

            buffer.append((char) c);
        }

        return Integer.parseInt(buffer.toString().trim(), 16);
    }

    @Override
    public void read() throws IOException {
        if (bodyObserved && !bodyComplete)
            ByteUtil.readStream(getContentAsStream());
        bodyObserved = true;
        super.read();
    }

    @Override
    public void write(AbstractBodyTransferrer out, boolean retainCopy) throws IOException {
        if (bodyObserved && !bodyComplete)
            ByteUtil.readStream(getContentAsStream());
        super.write(out, retainCopy);
    }

    @Override
    protected void markAsRead() {
        super.markAsRead();
        bodyComplete = true;
    }

    @Override
    protected void readLocal() throws IOException {
        List<Chunk> chunkList = readChunks(inputStream);
        chunks.addAll(chunkList);
        trailer = readTrailer(inputStream);
        for (Chunk chunk : chunkList)
            for (MessageObserver observer : observers)
                observer.bodyChunk(chunk);
    }

    @Override
    public void discard() throws IOException {
        if (read)
            return;
        if (wasStreamed())
            return;

        for (MessageObserver observer : observers)
            observer.bodyRequested(this);

        readChunksAndDrop(inputStream, observers);
        trailer = readTrailer(inputStream);
        markAsRead();
    }

    boolean bodyObserved = false;
    boolean bodyComplete = false;

    public InputStream getContentAsStream() {
        read = true;

        if (!bodyObserved) {
            bodyObserved = true;
            for (MessageObserver observer : observers)
                observer.bodyRequested(this);
            chunks.clear();
        }

        return new BodyInputStream(chunks) {
            @Override
            protected Chunk readNextChunk() throws IOException {
                if (bodyComplete)
                    return null;
                int chunkSize = readChunkSize(inputStream);
                if (chunkSize > 0) {
                    Chunk c = new Chunk(ByteUtil.readByteArray(inputStream, chunkSize));
                    //noinspection ResultOfMethodCallIgnored
                    inputStream.read(); // CR
                    //noinspection ResultOfMethodCallIgnored
                    inputStream.read(); // LF
                    for (MessageObserver observer : observers)
                        observer.bodyChunk(c);
                    return c;
                } else {
                    trailer = readTrailer(inputStream);

                    bodyComplete = true;

                    for (MessageObserver observer : observers)
                        observer.bodyComplete(ChunkedBody.this);
                    observers.clear();

                    return null;
                }
            }
        };
    }

    @Override
    protected void writeNotRead(AbstractBodyTransferrer out) throws IOException {
        log.debug("writeNotReadChunked");
        int chunkSize;
        while ((chunkSize = readChunkSize(inputStream)) > 0) {
            Chunk chunk = new Chunk(ByteUtil.readByteArray(inputStream, chunkSize));
            out.write(chunk);
            chunks.add(chunk);
            for (MessageObserver observer : observers)
                observer.bodyChunk(chunk);
            //noinspection ResultOfMethodCallIgnored
            inputStream.read(); // CR
            //noinspection ResultOfMethodCallIgnored
            inputStream.read(); // LF
        }
        trailer = readTrailer(inputStream);
        out.finish(trailer);
        markAsRead();
    }

    @Override
    protected void writeStreamed(AbstractBodyTransferrer out) throws IOException {
        log.debug("writeStreamed");
        int chunkSize;
        while ((chunkSize = readChunkSize(inputStream)) > 0) {
            Chunk chunk = new Chunk(ByteUtil.readByteArray(inputStream, chunkSize));
            out.write(chunk);
            for (MessageObserver observer : observers)
                observer.bodyChunk(chunk);
            //noinspection ResultOfMethodCallIgnored
            inputStream.read(); // CR
            //noinspection ResultOfMethodCallIgnored
            inputStream.read(); // LF
            lengthStreamed += chunkSize;
        }
        trailer = readTrailer(inputStream);
        out.finish(trailer);
        markAsRead();
    }

    protected int getRawLength() throws IOException {
        if (chunks.isEmpty())
            return 0;
        int length = getLength();
        for (Chunk chunk : chunks) {
            length += toHexString(chunk.getLength()).getBytes(UTF_8).length;
            length += 2 * CRLF_BYTES.length;
        }
        length += "0".getBytes(UTF_8).length;
        length += 2 * CRLF_BYTES.length;
        return length;
    }

    @Override
    protected byte[] getRawLocal() throws IOException {
        byte[] raw = new byte[getRawLength()];
        int destPos = 0;
        for (Chunk chunk : chunks) {
            destPos = chunk.copyChunkLength(raw, destPos, this);
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
    protected void writeAlreadyRead(AbstractBodyTransferrer out) throws IOException {
        if (getLength() > 0)
            for (Chunk chunk : chunks) {
                out.write(chunk);
            }
        out.finish(trailer);
    }

    @Override
    public int getLength() throws IOException {
        if (wasStreamed())
            return (int) lengthStreamed;
        return super.getLength();
    }

    @Override
    public boolean isRead() {
        return super.isRead() && bodyComplete;
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
