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