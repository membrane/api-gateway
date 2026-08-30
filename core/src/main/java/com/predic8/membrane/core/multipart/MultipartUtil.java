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
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.ParseException;
import org.apache.commons.fileupload.MultipartStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utility for splitting multipart HTTP messages into their individual {@link Part}s.
 *
 * <p>Example:
 * <pre>{@code
 * List<Part> parts = MultipartUtil.split(exchange.getRequest());
 * for (Part part : parts) {
 *     String name = part.getName();          // form field name
 *     String type = part.getContentType();   // e.g. "image/png"
 *     byte[] body = part.getBody();
 * }
 * }</pre>
 */
public class MultipartUtil {

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
     * <p>An XOP/MTOM message is reassembled first and passed to the handler as a single part.</p>
     *
     * <p>Content-Encodings (gzip, deflate, brotli) are decoded; the bodies passed to the handler are
     * the logical content.</p>
     *
     * @param message     a multipart request or response
     * @param maxPartSize maximum number of bytes a single part's body may occupy
     * @throws IOException    on I/O or parse errors, if a part is itself multipart, or if a part
     *                        exceeds {@code maxPartSize}
     * @throws ParseException if the Content-Type header cannot be parsed
     */
    @SuppressWarnings("deprecation")
    public static void forEachPart(Message message, int maxPartSize, PartHandler handler) throws IOException, ParseException {
        Message reconstituted = reconstituteXOP(message);
        if (reconstituted != null) {
            if (handler.decide(reconstituted.getHeader()) == PartAction.INSPECT)
                handler.handle(new Part(reconstituted.getHeader(), MessageUtil.getContent(reconstituted)));
            return;
        }

        MultipartStream ms = new MultipartStream(MessageUtil.getContentAsStream(message), boundaryOf(message).getBytes(UTF_8));
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
                    } catch (PartTooLargeException e) {
                        throw new IOException(e.getMessage());
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
                throw new PartTooLargeException(limit);
        }
    }

    /** Unchecked so it can escape {@link java.io.OutputStream#write}; converted by {@link #forEachPart}. */
    static class PartTooLargeException extends RuntimeException {
        PartTooLargeException(int limit) {
            super("Part exceeds the maximum size of " + limit + " bytes.");
        }
    }

    /**
     * @return the reassembled message of an XOP/MTOM multipart, or null if the message is not one
     */
    private static Message reconstituteXOP(Message message) {
        try {
            return new XOPReconstitutor().getReconstitutedMessage(message);
        } catch (Exception e) {
            // Not a well-formed XOP message; fall back to treating it as ordinary multipart.
            return null;
        }
    }

    /**
     * @return whether the message's Content-Type has a primary type of {@code multipart}
     */
    public static boolean isMultipart(Message message) throws ParseException {
        var contentType = message.getHeader().getContentTypeObject();
        return contentType != null && "multipart".equalsIgnoreCase(contentType.getPrimaryType());
    }

    private static boolean isMultipart(String contentType) {
        if (contentType == null)
            return false;
        try {
            return "multipart".equalsIgnoreCase(new ContentType(contentType).getPrimaryType());
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Splits a multipart message into its individual parts.
     * The MIME boundary is read from the message's {@code Content-Type} header.
     *
     * @param message a request or response whose Content-Type is multipart/*
     * @return parts in wire order; never null, may be empty
     * @throws IOException    on I/O or parse errors
     * @throws ParseException if the Content-Type header cannot be parsed
     */
    public static List<Part> split(Message message) throws IOException, ParseException {
        return split(message, boundaryOf(message));
    }

    private static String boundaryOf(Message message) throws IOException, ParseException {
        var contentType = message.getHeader().getContentTypeObject();
        if (contentType == null) {
            throw new IOException("No Content-Type header");
        }
        var boundary = contentType.getParameter("boundary");
        if (boundary == null) {
            throw new IOException("No boundary parameter in Content-Type: " + contentType);
        }
        return boundary;
    }

    /**
     * Splits a multipart message into its individual parts using an explicit boundary.
     *
     * @param message  a request or response with a multipart body
     * @param boundary the MIME boundary string (without leading {@code --})
     * @return parts in wire order; never null, may be empty
     * @throws IOException on I/O or unsupported Content-Transfer-Encoding
     */
    @SuppressWarnings("deprecation")
    public static List<Part> split(Message message, String boundary) throws IOException {
        List<Part> result = new ArrayList<>();

        MultipartStream ms = new MultipartStream(MessageUtil.getContentAsStream(message), boundary.getBytes(UTF_8));
        boolean hasNext = ms.skipPreamble();
        while (hasNext) {
            Header partHeader = new Header(ms.readHeaders());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ms.readBodyData(baos);
            checkContentTransferEncoding(partHeader);

            result.add(new Part(partHeader, baos.toByteArray()));
            hasNext = ms.readBoundary();
        }
        return result;
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
}
