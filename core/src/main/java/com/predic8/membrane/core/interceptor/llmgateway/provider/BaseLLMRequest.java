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

package com.predic8.membrane.core.interceptor.llmgateway.provider;

import com.predic8.membrane.core.exchange.Exchange;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;

public class BaseLLMRequest extends AbstractLLMMessage implements LLMRequest {

    public static final String BEARER_PREFIX = "Bearer";

    public BaseLLMRequest(Exchange exchange) {
        super(exchange);
    }

    /**
     * The header the provider expects its api key in. Overridden by providers that use a key header
     * of their own instead of {@code Authorization}.
     */
    protected String apiKeyHeaderName() {
        return AUTHORIZATION;
    }

    /**
     * Whether the key is carried as a {@code Bearer} token or as the plain header value.
     */
    protected boolean apiKeyIsBearer() {
        return true;
    }

    @Override
    public final void setApiKey(String apiKey) {
        var header = exchange.getRequest().getHeader();
        header.removeFields(apiKeyHeaderName());
        header.add(apiKeyHeaderName(), apiKeyIsBearer() ? BEARER_PREFIX + " " + apiKey : apiKey);
    }

    @Override
    public final String getApiKey() {
        var value = exchange.getRequest().getHeader().getFirstValue(apiKeyHeaderName());
        if (value == null) {
            return null;
        }

        if (!apiKeyIsBearer()) {
            return value;
        }

        if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        var token = value.substring(BEARER_PREFIX.length()).trim();

        return token.isEmpty() ? null : token;
    }

}
