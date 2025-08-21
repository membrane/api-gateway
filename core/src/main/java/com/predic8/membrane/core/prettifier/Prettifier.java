/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.prettifier;

import org.slf4j.*;

import java.io.*;
import java.nio.charset.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.charset.StandardCharsets.*;

public interface Prettifier {

    Logger log = LoggerFactory.getLogger(Prettifier.class);

    /**
     * Returns a prettified representation of the given bytes using the provided charset.
     * Implementations may return the same reference (zero-copy) or a new byte array.
     */
    byte[] prettify(byte[] c, Charset charset);

    byte[] prettify(InputStream is, Charset charset) throws IOException;

    /**
     * Convenient method that assumes UTF-8 as encoding
     */
    default byte[] prettify(byte[] c) {
        return prettify(c, UTF_8);
    }

    static Prettifier getInstance(String contentType) {
        if (contentType == null)
            return NullPrettifier.INSTANCE;

        String ct = contentType.trim();

        // JSON family: application/json, application/*+json, with or without charset/params
        if (isJson(ct))
            return JSONPrettifier.INSTANCE;

        // XML/HTML family: text/xml, application/xml, text/html (and charset variants)
        if (isXML(ct) || isHtml(ct))
            return XMLPrettifier.INSTANCE;

        if (isText(ct))
            return TextPrettifier.INSTANCE;

        return NullPrettifier.INSTANCE;
    }
}
