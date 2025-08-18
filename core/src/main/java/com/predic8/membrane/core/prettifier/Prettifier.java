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

import static com.predic8.membrane.core.http.MimeType.*;

public interface Prettifier {

    Prettifier JSON = new JSONPrettifier();
    Prettifier XML = new XMLPrettifier();
    Prettifier TEXT = new TextPrettifier();

    byte[] prettify(byte[] c) throws Exception;

    static Prettifier getInstance(String contentType) {
        if (contentType == null)
            return TEXT;

        String ct = contentType.toLowerCase(java.util.Locale.ROOT).trim();

        // JSON family: application/json, application/*+json, with or without charset/params
        if (isJson(ct))
            return JSON;

        // XML/HTML family: text/xml, application/xml, text/html (and charset variants)
        if (isXML(ct) || isHtml(ct))
            return XML;

        return TEXT;
    }
}
