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
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static com.predic8.membrane.core.util.URLParamUtil.*;
import static java.lang.String.*;
import static java.util.Optional.*;

/**
 * @description Extracts an API key from a URL query parameter. By default, the parameter name
 * is {@code api-key}. If the parameter is present in the request URI, its value is returned as the API key.
 * Parameter name matching is case-insensitive.
 * <p>
 * Example usage inside {@code &lt;apiKey&gt;}:
 * </p>
 * <pre>
 * &lt;apiKey&gt;
 *   &lt;queryParamExtractor /&gt; <!-- default: api-key -->
 *
 *   &lt;!-- custom parameter name --&gt;
 *   &lt;queryParamExtractor name="api_key"/&gt;
 * &lt;/apiKey&gt;
 * </pre>
 * @topic 3. Security and Validation
 */
@MCElement(name="queryParamExtractor", topLevel = false)
public class ApiKeyQueryParamExtractor implements ApiKeyExtractor{

    private static final Logger log = LoggerFactory.getLogger(ApiKeyQueryParamExtractor.class);

    private String paramName = "api-key";

    @Override
    public Optional<LocationNameValue> extract(Exchange exc) {
        Map<String, String> queryParams;
        try {
            queryParams = new TreeMap<>(CASE_INSENSITIVE_ORDER); // Handle key names case insensitive
            queryParams.putAll(getParams(new URIFactory(), exc, ERROR));
        } catch (Exception e) {
            log.info("Error extracting query parameters. From " + exc.getRequest().getUri());
            return empty();
        }

        if (queryParams.containsKey(paramName.toLowerCase())) {
            return Optional.of(new LocationNameValue(QUERY, paramName, queryParams.get(paramName.toLowerCase())));
        }

        return empty();
    }

    /**
     * @description The query parameter name to check for an API key.
     * @default api-key
     * @example api_key
     */
    @MCAttribute(attributeName = "name")
    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    @SuppressWarnings("unused")
    public String getParamName() {
        return paramName;
    }

    @Override
    public String getDescription() {
        return "Query Param name: " + paramName + ". ";
    }
}