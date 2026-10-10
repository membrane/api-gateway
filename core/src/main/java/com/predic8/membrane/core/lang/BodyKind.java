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
package com.predic8.membrane.core.lang;

import com.predic8.membrane.core.http.Message;

import java.util.function.Predicate;

import static com.predic8.membrane.core.http.MimeType.*;

/**
 * How an expression language that queries the body (XPath, JSONPath) treats a message, judged from
 * its Content-Type before the body is parsed.
 * <p>
 * A body that cannot hold a document of the language is evaluated against an empty document rather
 * than answered with a fixed result, so that every expression keeps the meaning the language gives
 * it for a document without the queried content: {@code /a} is false and {@code not(/a)} is true.
 * Only a body that should be such a document but does not parse is an error.
 */
public enum BodyKind {

    /**
     * No body: evaluated against an empty document.
     */
    EMPTY,

    /**
     * Declares the language's own format: parsed, a parse failure is an error.
     */
    MATCHING,

    /**
     * Declares no usable format (no Content-Type or text/plain): parsed, a parse failure is an error.
     */
    UNKNOWN,

    /**
     * Form data, which <code>curl -d</code> sends for any body: parsed, but a body that does not parse
     * is evaluated against an empty document, because its Content-Type said it is no such document.
     */
    FORM,

    /**
     * Declares another format: not parsed, evaluated against an empty document.
     */
    FOREIGN;

    /**
     * @param isOwnType whether a Content-Type declares the language's own format
     */
    public static BodyKind of(Message msg, Predicate<String> isOwnType) {
        var kind = byContentType(msg.getHeader().getContentType(), isOwnType);
        if (kind == FOREIGN)
            return FOREIGN; // Not parsed, so the body need not be read to tell whether it is empty
        return isEmpty(msg) ? EMPTY : kind;
    }

    private static BodyKind byContentType(String contentType, Predicate<String> isOwnType) {
        if (contentType == null || isOfMediaType(TEXT_PLAIN, contentType))
            return UNKNOWN;
        if (isOwnType.test(contentType))
            return MATCHING;
        if (isWWWFormUrlEncoded(contentType))
            return FORM;
        return FOREIGN;
    }

    /**
     * A chunked body announces no length, so it has to be read to tell. Parsing reads it anyway.
     */
    private static boolean isEmpty(Message msg) {
        return msg.isBodyEmpty() || msg.getBody().getContent().length == 0;
    }
}
