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

import com.predic8.membrane.core.exceptions.ProblemDetails;
import jakarta.mail.internet.ParseException;

import static com.predic8.membrane.core.util.text.TextUtil.escapeControlCharsForLog;

/**
 * Indicates that the {@code Content-Type} header of a message cannot be parsed, so neither its
 * media type nor its charset is known.
 * <p>
 * Travels wrapped in a {@link ReadingBodyException}, whose source says which message the header
 * belongs to.
 */
public class ContentTypeException extends Exception implements MessageProblem {

    private final String contentType;

    public ContentTypeException(String contentType, ParseException cause) {
        // Logged at info, so escape the parser's wording: it quotes the header value
        super("Content-Type header could not be parsed: " + escapeControlCharsForLog(cause.getMessage()), cause);
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    /**
     * Production logs the detail and the internal fields at info, so both escape the header value.
     */
    @Override
    public ProblemDetails describe(ProblemDetails problem) {
        return problem.addSubType("content-type")
                .title("Content-Type header could not be parsed")
                .detail("The Content-Type header \"%s\" could not be parsed.".formatted(escapeControlCharsForLog(contentType)), true)
                .internal("reason", escapeControlCharsForLog(getCause().getMessage()));
    }
}
