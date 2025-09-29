/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;

import java.util.*;

import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.*;

/**
 * @description Extracts an API key from a specific HTTP request header. By default, the header name
 * is <code>X-Api-Key</code>. If the header is present, its first value is returned as the API key.
 * <p>
 * Example usage inside <code>&lt;apiKey&gt;</code>:
 * </p>
 * <pre><code><apiKey>
 *   <headerExtractor /> <!-- default: X-Api-Key -->
 *
 *   <!-- custom header name -->
 *   <headerExtractor name="Authorization"/>
 * </apiKey></code></pre>
 * @topic 3. Security and Validation
 */
@MCElement(name="headerExtractor", topLevel = false)
public class ApiKeyHeaderExtractor implements ApiKeyExtractor{

    private HeaderName headerName = new HeaderName("X-Api-Key");

    @Override
    public Optional<LocationNameValue> extract(Exchange exc) {
        Header header = exc.getRequest().getHeader();

        if (header.contains(headerName)) {
            return Optional.of(new LocationNameValue(HEADER, headerName.getName(), header.getFirstValue(headerName)));
        }

        return Optional.empty();
    }

    /**
     * @description The HTTP header name to check for an API key.
     * @default X-Api-Key
     * @example Authorization
     */
    @MCAttribute(attributeName = "name")
    public void setHeaderName(String headerName) {
        this.headerName = new HeaderName(headerName);
    }

    public String getHeaderName() {
        return headerName.getName();
    }

    @Override
    public String getDescription() {
        return "Header Name: " + headerName + ". ";
    }
}