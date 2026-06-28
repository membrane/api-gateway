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

    protected BaseLLMRequest(Exchange exchange) {
        super(exchange);
    }

    @Override
    public void setApiKey(String apiKey) {
        exchange.getRequest().getHeader().removeFields(AUTHORIZATION);
        exchange.getRequest().getHeader().add(AUTHORIZATION, "Bearer " + apiKey);
    }

    @Override
    public String getApiKey() {
        var ah = exchange.getRequest().getHeader().getAuthorization();
        if (ah == null) {
            return null;
        }

        if (!ah.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        var token = ah.substring(BEARER_PREFIX.length()).trim();

        return token.isEmpty() ? null : token;
    }

}
