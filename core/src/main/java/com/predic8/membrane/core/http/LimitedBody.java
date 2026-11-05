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

package com.predic8.membrane.core.http;

import com.predic8.membrane.core.util.MessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static com.predic8.membrane.core.http.Header.*;

/**
 * The decoded content of another body, which fails once more than a limit has been read. The content is
 * produced while it is read, so the body can be streamed: the first bytes are passed on before the last
 * ones have arrived, and nothing is held in memory unless the body is read.
 * <p>
 * Owns the body it wraps. Whichever way this body is consumed, the wrapped one is consumed to its end too,
 * so the connection it is read from stays in step: a decoder stops at the end of its own data and leaves,
 * for example, the last chunk unread. If the limit is exceeded or reading fails, both bodies are marked
 * failed. The connection the wrapped body is read from is then closed instead of being reused, as the
 * rest of the body is still in it.
 * <p>
 * The observers registered on the wrapped body stay there and see its raw bytes. Observers registered on
 * this body see the decoded ones.
 */
public class LimitedBody extends AbstractBody {

    private static final Logger log = LoggerFactory.getLogger(LimitedBody.class);

    private static final int BUFFER_SIZE = 8192;

    private final AbstractBody original;
    private final String contentEncoding;
    private final Message message;
    private final long limit;

    private long length;
    private long streamedLength;

    private LimitedBody(AbstractBody original, String contentEncoding, Message message, long limit) {
        this.original = original;
        this.contentEncoding = contentEncoding;
        this.message = message;
        this.limit = limit;
    }

    /**
     * Replaces the body of the message with its decoded content, limited to <code>limit</code> bytes. Unlike
     * {@link Message#setBody(AbstractBody)}, the old body is not discarded, since the new one reads it.
     * <p>
     * The length is not known before the body has been read, so <code>Content-Length</code> is removed, and
     * as the content is decoded, <code>Content-Encoding</code> is removed too.
     *
     * @param limit   the largest number of decoded bytes the body may have
     * @param chunked whether to frame the message with <code>Transfer-Encoding: chunked</code>. Otherwise the
     *                body ends when the connection is closed, as for HTTP/1.0, which has no chunked coding.
     */
    public static void install(Message message, long limit, boolean chunked) {
        final var header = message.getHeader();
        message.body = new LimitedBody(message.getBody(), header.getContentEncoding(), message, limit);
        header.removeFields(CONTENT_ENCODING);
        header.removeFields(CONTENT_LENGTH);
        if (chunked)
            header.setValue(TRANSFER_ENCODING, CHUNKED);
        else
            header.removeFields(TRANSFER_ENCODING);
    }

    @Override
    protected void readLocal() throws IOException {
        try (final var decoded = openDecoded()) {
            final var buffer = new byte[BUFFER_SIZE];
            int n;
            while ((n = readLimited(decoded, buffer)) != -1) {
                final var chunk = new Chunk(Arrays.copyOf(buffer, n));
                chunks.add(chunk);
                for (MessageObserver observer : observers)
                    observer.bodyChunk(chunk);
            }
        } catch (ReadingBodyException e) {
            // From the decoder, unchecked by design (see MessageUtil). read() only records IOExceptions.
            original.fail(new IOException(e.getMessage(), e));
            throw new IOException(e.getMessage(), e);
        } catch (IOException e) {
            original.fail(e);
            throw e;
        }
        drainOriginal();
    }

    @Override
    protected void writeStreamed(AbstractBodyTransferer out) {
        try (final var decoded = openDecoded()) {
            final var buffer = new byte[BUFFER_SIZE];
            int n;
            while ((n = readLimited(decoded, buffer)) != -1) {
                try {
                    out.write(buffer, 0, n);
                } catch (IOException e) {
                    throw new WritingBodyException(e);
                }
                streamedLength += n;
                for (MessageObserver observer : observers)
                    observer.bodyChunk(buffer, 0, n);
            }
        } catch (ReadingBodyException e) {
            throw failBoth(new IOException(e.getMessage(), e));
        } catch (IOException e) {
            throw failBoth(e);
        }
        try {
            drainOriginal();
        } catch (IOException e) {
            throw fail(e);
        }
        try {
            out.finish(null);
        } catch (IOException e) {
            throw new WritingBodyException(e);
        }
        markAsRead();
    }

    /**
     * Like {@link AbstractBody#discard()}, which reads the body through the limit rather than only draining
     * the wrapped one: observers registered on this body still get their terminal event. If a transfer broke
     * off, e.g. because the receiver went away, the rest of the wrapped body is still unread, so both are
     * marked failed instead.
     */
    @Override
    public void discard() {
        if (wasStreamed() && !isRead() && !hasFailed()) {
            failBoth(new IOException("The transfer of the body was interrupted."));
            return;
        }
        super.discard();
    }

    @Override
    protected void writeAlreadyRead(AbstractBodyTransferer out) throws IOException {
        read(); // reached with an unread body when a copy is retained
        for (Chunk chunk : chunks)
            out.write(chunk);
        out.finish(null);
    }

    @Override
    protected byte[] getRawLocal() {
        return getContent();
    }

    /**
     * @return for a streamed body, the number of bytes streamed so far; otherwise, as in
     *         {@link AbstractBody#getLength()}
     */
    @Override
    public long getLength() {
        if (wasStreamed())
            return streamedLength;
        return super.getLength();
    }

    private InputStream openDecoded() {
        length = 0;
        return MessageUtil.getContentAsStream(original, contentEncoding, message);
    }

    private int readLimited(InputStream decoded, byte[] buffer) throws IOException {
        final int n = decoded.read(buffer);
        if (n == -1)
            return n;
        length += n;
        if (length > limit) {
            log.info("Message length >= {} exceeded limit {}.", length, limit);
            throw new LimitExceededException(limit);
        }
        return n;
    }

    /**
     * Reads the wrapped body to its end, e.g. the last chunk a decoder left unread, which completes it and
     * lets its connection be reused.
     */
    private void drainOriginal() throws IOException {
        try {
            original.discard();
        } catch (ReadingBodyException e) {
            // the wrapped body has recorded the failure already
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * The wrapped body first: failing it closes the connection it is read from.
     */
    private ReadingBodyException failBoth(IOException e) {
        original.fail(e);
        return fail(e);
    }
}
