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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.function.Predicate;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.text.TextUtil.detectEncoding;

/**
 * How an expression language that queries the body (XPath, JSONPath) treats a message, judged from
 * its Content-Type and the first bytes of its body.
 * <p>
 * A body that cannot hold a document of the language is evaluated against an empty document rather
 * than answered with a fixed result, so that every expression keeps the meaning the language gives
 * it for a document without the queried content: {@code /a} is false and {@code not(/a)} is true.
 * Only a body that should be such a document but does not parse is an error.
 * <p>
 * The Content-Type alone does not rule a body out: a backend may parse the body whatever its
 * Content-Type says, so a guard that skipped a mislabelled document could be bypassed.
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
     * Declares form data or another format, but starts like a document of the language: parsed, a
     * parse failure is an error. Falling back to an empty document would let a body pass that the
     * gateway's parser rejects but a lenient backend accepts.
     */
    MISLABELLED,

    /**
     * Form data, which <code>curl -d</code> sends for any body, that does not start like a document of
     * the language: evaluated against an empty document, unless the whole body is a document the
     * language can tell apart from form data, such as a JSON scalar.
     */
    FORM,

    /**
     * Declares another format and does not start like a document of the language: evaluated against
     * an empty document, unless the whole body is a document the language can still recognise, such as
     * a JSON scalar.
     */
    FOREIGN;

    /**
     * @param isOwnType whether a Content-Type declares the language's own format
     * @param documentStart the characters a document of the language can start with, after whitespace
     */
    public static BodyKind of(Message msg, Predicate<String> isOwnType, String documentStart) {
        if (isEmpty(msg))
            return EMPTY;
        var kind = byContentType(msg.getHeader().getContentType(), isOwnType);
        if ((kind == FORM || kind == FOREIGN) && startsLikeDocument(msg, documentStart))
            return MISLABELLED;
        return kind;
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
        return msg.isBodyEmpty() || msg.getBody().getLength() == 0;
    }

    /**
     * Looks at the first character that is not whitespace, decoded in the encoding the first bytes
     * point to. Bytes alone would not do: a document in UTF-16 or UTF-32 starts with a byte order mark
     * or a zero byte, but so do binary formats such as JPEG (0xFF) or gRPC (0x00).
     */
    static boolean startsLikeDocument(Message msg, String documentStart) {
        try (var in = new BufferedInputStream(msg.getBodyAsStreamDecoded())) {
            var reader = new InputStreamReader(in, detectEncoding(in));
            int c;
            do {
                c = reader.read();
            } while (c == ' ' || c == '\t' || c == '\r' || c == '\n');
            return c != -1 && documentStart.indexOf(c) >= 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
