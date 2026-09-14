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

import java.io.IOException;

/**
 * Indicates that a body could not be decoded with the Content-Encoding it was labelled with: a gzip
 * stream that stops short, bytes that are not in the declared format at all, a deflate stream framed
 * differently than the decoder expects.
 * <p>
 * Always travels wrapped in a {@link ReadingBodyException}, whose source says which message the
 * undecodable body belonged to. Keeping the coding in a field of its own lets
 * {@link com.predic8.membrane.core.exceptions.ProblemDetails#bodyFailure} name it to the sender in
 * production, where the detail text is withheld.
 */
public class DecodingException extends IOException {

    private final String contentEncoding;

    public DecodingException(String contentEncoding, IOException cause) {
        super("Could not decode body with Content-Encoding \"%s\": %s".formatted(contentEncoding, describe(cause)), cause);
        this.contentEncoding = contentEncoding;
    }

    /**
     * Truncating a gzip body inside its trailer raises an {@link java.io.EOFException} carrying no
     * message at all, so name the type rather than leave the explanation blank.
     */
    private static String describe(IOException cause) {
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    public String getContentEncoding() {
        return contentEncoding;
    }
}
