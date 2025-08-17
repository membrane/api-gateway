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

package com.predic8.membrane.core.beautifier;

import static com.predic8.membrane.core.http.MimeType.*;

public interface Prettifier {

    byte[] prettify(byte[] c) throws Exception;

    static Prettifier getInstance(String contentType) {
        return switch (contentType) {
            case APPLICATION_JSON -> new JSONPrettifier();
            case APPLICATION_XML, APPLICATION_SOAP, TEXT_HTML, TEXT_XML, TEXT_HTML_UTF8, TEXT_XML_UTF8 ->
                    new XMLPrettifier();
            default -> new TextPrettifier();
        };
    }
}
