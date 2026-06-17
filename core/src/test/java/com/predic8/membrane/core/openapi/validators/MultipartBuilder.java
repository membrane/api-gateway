/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators;

import static com.predic8.membrane.annot.Constants.CRLF;

/**
 * Test helper that assembles a raw {@code multipart/form-data} body using CRLF line breaks as
 * required by the multipart parser. Use {@link #CONTENT_TYPE} for the request's Content-Type header.
 */
public class MultipartBuilder {

    public static final String BOUNDARY = "abc123";
    public static final String CONTENT_TYPE = "multipart/form-data; boundary=" + BOUNDARY;

    private final StringBuilder sb = new StringBuilder();

    public MultipartBuilder part(String name, String filename, String contentType, String transferEncoding, String content) {
        sb.append("--").append(BOUNDARY).append(CRLF);
        sb.append("Content-Disposition: form-data; name=\"").append(name).append("\"");
        if (filename != null)
            sb.append("; filename=\"").append(filename).append("\"");
        sb.append(CRLF);
        if (contentType != null)
            sb.append("Content-Type: ").append(contentType).append(CRLF);
        if (transferEncoding != null)
            sb.append("Content-Transfer-Encoding: ").append(transferEncoding).append(CRLF);
        sb.append(CRLF);
        sb.append(content).append(CRLF);
        return this;
    }

    public String build() {
        return sb + "--" + BOUNDARY + "--" + CRLF;
    }
}
