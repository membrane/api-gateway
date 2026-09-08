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

package com.predic8.membrane.core.interceptor.protection;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.multipart.Part;

/**
 * Where an inspected document came from: the whole body of a message, or one part of a multipart
 * body. This decides how a violation is worded, so that a rejection on an upload can be traced back
 * to one attachment, and it carries the index the part was found at, so a rewritten part can be put
 * back where it came from.
 *
 * @param header the document's own header, the message's or the part's
 * @param name   what to call the part in a message to the client, or {@code null} for a whole body
 *               and for a part carrying neither a form field name nor a Content-ID
 * @param index  the part's position in the multipart body, or {@code -1} for a whole body
 */
public record Origin(Header header, String name, int index) {

    /** The whole body of a message. */
    public static Origin body(Header header) {
        return new Origin(header, null, -1);
    }

    /** The part at {@code index}, named as {@link #describe} will refer to it. */
    public static Origin part(Header partHeader, int index) {
        String name = Part.nameOf(partHeader);
        return new Origin(partHeader, name != null ? name : Part.contentIDOf(partHeader), index);
    }

    public boolean isPart() {
        return index >= 0;
    }

    public String contentType() {
        return header.getContentType();
    }

    /**
     * Puts a message about this document in front of the client: unchanged for a whole body, and
     * naming the offending part for anything found inside a multipart body.
     */
    public String describe(String message) {
        if (!isPart())
            return message;
        return name == null ? "In one part of the multipart body: " + message
                            : "In part '%s': %s".formatted(name, message);
    }
}
