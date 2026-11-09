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

import jakarta.mail.internet.ParseException;

import static com.predic8.membrane.core.util.text.TextUtil.escapeControlCharsForLog;

/**
 * Indicates that the {@code Content-Type} header of a message cannot be parsed, so neither its
 * media type nor its charset is known.
 * <p>
 * Travels wrapped in a {@link ReadingBodyException}, whose source says which message the header
 * belongs to.
 */
public class InvalidContentTypeException extends Exception {

    private final String contentType;

    public InvalidContentTypeException(String contentType, ParseException cause) {
        // Logged at info, so escape the parser's wording: it quotes the header value
        super("Content-Type header could not be parsed: " + escapeControlCharsForLog(cause.getMessage()), cause);
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }
}
