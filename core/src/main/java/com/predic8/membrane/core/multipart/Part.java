/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A single part of a multipart HTTP message, consisting of a header block and a body.
 *
 * @see MultipartUtil#split(com.predic8.membrane.core.http.Message)
 */
public class Part {

    private static final Pattern NAME_PATTERN =
            Pattern.compile("(?i)\\bname=\"([^\"]+)\"");
    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("(?i)\\bfilename=\"([^\"]+)\"");

    private final Header header;
    private final byte[] body;

    public Part(Header header, byte[] body) {
        this.header = header;
        this.body = body;
    }

    // -------------------------------------------------------------------------
    // Header accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the part's own header block (may contain Content-Type, Content-ID, etc.).
     */
    public Header getHeader() {
        return header;
    }

    /**
     * Returns the {@code Content-ID} header value, or {@code null} if absent.
     * Used in MIME multipart/related messages (e.g. SOAP XOP).
     */
    public String getContentID() {
        return header.getFirstValue("Content-ID");
    }

    /**
     * Returns the {@code Content-Type} of this part (e.g. {@code "image/png"}),
     * or {@code null} if no Content-Type header is present.
     */
    public String getContentType() {
        return header.getContentType();
    }

    /**
     * Returns the {@code name} parameter from the {@code Content-Disposition} header.
     * This is the form field name in {@code multipart/form-data} submissions.
     * Returns {@code null} if not present.
     */
    public String getName() {
        return extractDispositionParam(NAME_PATTERN);
    }

    /**
     * Returns the {@code filename} parameter from the {@code Content-Disposition} header,
     * or {@code null} if not present.
     */
    public String getFilename() {
        return extractDispositionParam(FILENAME_PATTERN);
    }

    // -------------------------------------------------------------------------
    // Body accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the raw body bytes of this part.
     */
    public byte[] getBody() {
        return body;
    }

    /**
     * Returns the body decoded as a UTF-8 string.
     */
    public String getBodyAsString() {
        return getBodyAsString(UTF_8);
    }

    /**
     * Returns the body decoded using the given charset.
     */
    public String getBodyAsString(Charset charset) {
        return new String(body, charset);
    }

    /**
     * Returns a fresh {@link InputStream} over the body bytes.
     */
    public InputStream getInputStream() {
        return new ByteArrayInputStream(body);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String extractDispositionParam(Pattern pattern) {
        String disposition = header.getFirstValue("Content-Disposition");
        if (disposition == null) return null;
        Matcher m = pattern.matcher(disposition);
        return m.find() ? m.group(1) : null;
    }
}
