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
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.ParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
 *
 * <p>Every part is buffered; see {@link PartScanner} to traverse a message without doing that.</p>
 */
public class MultipartUtil {

    /**
     * @return whether the message's Content-Type has a primary type of {@code multipart}
     */
    public static boolean isMultipart(Message message) throws ParseException {
        var contentType = message.getHeader().getContentTypeObject();
        return contentType != null && "multipart".equalsIgnoreCase(contentType.getPrimaryType());
    }

    static boolean isMultipart(String contentType) {
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
     * @throws IOException    on I/O or parse errors, or if a part is itself multipart
     * @throws ParseException if the Content-Type header cannot be parsed
     */
    public static List<Part> split(Message message) throws IOException, ParseException {
        return split(message, boundaryOf(message));
    }

    static String boundaryOf(Message message) throws IOException, ParseException {
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
     * <p>Buffers every part, so use {@link PartScanner#forEachPart} when the parts can be large or
     * when only some of them are needed.</p>
     *
     * @param message  a request or response with a multipart body
     * @param boundary the MIME boundary string (without leading {@code --})
     * @return parts in wire order; never null, may be empty
     * @throws IOException on I/O errors, on unsupported Content-Transfer-Encoding, or if a part is
     *                     itself multipart
     */
    public static List<Part> split(Message message, String boundary) throws IOException {
        List<Part> result = new ArrayList<>();
        PartScanner.forEachPart(message, boundary, Integer.MAX_VALUE, new PartScanner.PartHandler() {
            @Override
            public PartScanner.PartAction decide(Header partHeader) {
                return PartScanner.PartAction.INSPECT;
            }

            @Override
            public void handle(Part part) {
                result.add(part);
            }
        });
        return result;
    }

}
