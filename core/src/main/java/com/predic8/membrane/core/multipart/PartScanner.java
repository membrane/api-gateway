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

package com.predic8.membrane.core.multipart;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.util.MessageUtil;
import jakarta.mail.internet.ParseException;
import org.apache.commons.fileupload.MultipartStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.predic8.membrane.core.multipart.MultipartUtil.boundaryOf;
import static com.predic8.membrane.core.multipart.MultipartUtil.isMultipart;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Traverses a multipart message part by part, letting the caller decide from each part's header
 * whether its body is needed at all. Use this over {@link MultipartUtil#split} whenever the parts
 * can be large or only some of them are of interest.
 *
 * <p>Example:
 * <pre>{@code
 * PartScanner.forEachPart(exchange.getRequest(), 1024 * 1024, new PartScanner.PartHandler() {
 *     public PartAction decide(Header partHeader) {
 *         return isJson(partHeader.getContentType()) ? PartAction.INSPECT : PartAction.SKIP;
 *     }
 *     public void handle(Part part) {
 *         // only the JSON parts ever reach this point, and each is bounded
 *     }
 * });
 * }</pre>
 */
public class PartScanner {

    /** What {@link PartHandler} wants done with a part, decided from its header alone. */
    public enum PartAction {
        /** Buffer the body (bounded) and pass it to {@link PartHandler#handle}. */
        INSPECT,
        /** Discard the body without reading it into memory, and continue with the next part. */
        SKIP,
        /** Stop the traversal immediately; nothing further is read or buffered. */
        STOP
    }

    /**
     * Decides from a part's header alone whether its body is needed, so that unwanted parts are
     * never buffered.
     */
    public interface PartHandler {
        PartAction decide(Header partHeader);

        void handle(Part part) throws IOException;
    }

    /**
     * Streams the parts of a multipart message to a handler, holding at most one part body in memory
     * at a time. Parts the handler does not want are discarded unread, and a part exceeding
     * {@code maxPartSize} aborts the traversal instead of being buffered whole.
     *
     * <p>XOP/MTOM messages are traversed as their raw parts and are deliberately not reassembled:
     * reassembly would have to buffer every attachment and a base64-inflated copy of it before the
     * handler could decide anything. Callers that need the reassembled document (schema validation,
     * for example) use {@link XOPReconstitutor} directly.</p>
     *
     * <p>Content-Encodings (gzip, deflate, brotli) are decoded; the bodies passed to the handler are
     * the logical content.</p>
     *
     * @param message     a multipart request or response
     * @param maxPartSize maximum number of bytes a single part's body may occupy
     * @throws IOException    on I/O or parse errors, if a part is itself multipart, or, as
     *                        {@link PartTooLargeException}, if a part exceeds {@code maxPartSize}
     * @throws ParseException if the Content-Type header cannot be parsed
     */
    public static void forEachPart(Message message, int maxPartSize, PartHandler handler) throws IOException, ParseException {
        forEachPart(message, boundaryOf(message), maxPartSize, handler);
    }

    /**
     * Streams the parts to a handler using an explicit boundary, for callers that know it already.
     *
     * @see #forEachPart(Message, int, PartHandler)
     */
    @SuppressWarnings("deprecation")
    public static void forEachPart(Message message, String boundary, int maxPartSize, PartHandler handler) throws IOException {
        MultipartStream ms = new MultipartStream(MessageUtil.getContentAsStream(message), boundary.getBytes(UTF_8));
        boolean hasNext = ms.skipPreamble();
        while (hasNext) {
            Header partHeader = new Header(ms.readHeaders());
            // Everything that can reject a part is decided from its header, before any body byte is buffered.
            checkSupported(partHeader);

            switch (handler.decide(partHeader)) {
                case STOP -> {
                    return;
                }
                case SKIP -> ms.discardBodyData();
                case INSPECT -> {
                    BoundedOutputStream body = new BoundedOutputStream(maxPartSize);
                    try {
                        ms.readBodyData(body);
                    } catch (LimitExceeded e) {
                        throw new PartTooLargeException(partHeader, maxPartSize, e);
                    }
                    handler.handle(new Part(partHeader, body.toByteArray()));
                }
            }
            hasNext = ms.readBoundary();
        }
    }

    private static void checkSupported(Header partHeader) throws IOException {
        if (isMultipart(partHeader.getContentType()))
            throw new IOException("Nested multipart is not supported: part has Content-Type " + partHeader.getContentType());
        checkContentTransferEncoding(partHeader);
    }

    /** Only binary-safe encodings are supported; base64/QP would corrupt binary parts. */
    private static void checkContentTransferEncoding(Header partHeader) throws IOException {
        String cte = partHeader.getFirstValue("Content-Transfer-Encoding");
        if (cte != null && !cte.equalsIgnoreCase("binary")
                && !cte.equalsIgnoreCase("8bit")
                && !cte.equalsIgnoreCase("7bit")) {
            throw new IOException("Content-Transfer-Encoding '" + cte + "' is not supported.");
        }
    }

    /**
     * Buffers up to a fixed number of bytes and fails as soon as that is exceeded, so an oversized
     * part stops the read early instead of being materialised in full.
     */
    private static class BoundedOutputStream extends ByteArrayOutputStream {
        private final int limit;

        BoundedOutputStream(int limit) {
            this.limit = limit;
        }

        @Override
        public synchronized void write(int b) {
            checkRoomFor(1);
            super.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            checkRoomFor(len);
            super.write(b, off, len);
        }

        private void checkRoomFor(int additional) {
            if (size() + additional > limit)
                throw new LimitExceeded();
        }
    }

    /** Unchecked so it can escape {@link java.io.OutputStream#write}; converted by {@link #forEachPart}. */
    private static class LimitExceeded extends RuntimeException {
    }
}
