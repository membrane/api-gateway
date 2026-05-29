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
        var contentType = message.getHeader().getContentTypeObject();
        if (contentType == null) {
            throw new IOException("No Content-Type header");
        }
        String boundary = contentType.getParameter("boundary");
        if (boundary == null) {
            throw new IOException("No boundary parameter in Content-Type: " + contentType);
        }
        return split(message, boundary);
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

            // Only binary-safe encodings are supported; base64/QP would corrupt binary parts
            String cte = partHeader.getFirstValue("Content-Transfer-Encoding");
            if (cte != null && !cte.equalsIgnoreCase("binary")
                    && !cte.equalsIgnoreCase("8bit")
                    && !cte.equalsIgnoreCase("7bit")) {
                throw new IOException("Content-Transfer-Encoding '" + cte + "' is not supported.");
            }

            result.add(new Part(partHeader, baos.toByteArray()));
            hasNext = ms.readBoundary();
        }
        return result;
    }
}
