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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.util.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;

public abstract class AbstractLLMRequest extends AbstractLLMMessage implements LLMRequest {

    private static final Logger log = LoggerFactory.getLogger(AbstractLLMRequest.class);

    public static final String BEARER_PREFIX = "Bearer";

    protected ObjectNode json;

    public AbstractLLMRequest(Exchange exchange) {
        super(exchange);

        if (exchange.getRequest().isJSON()) {
            json = JsonUtil.getJsonObject(exchange.getRequest()).orElseThrow(() -> new RuntimeException("Cannot parse input as JSON message."));
        } else {
            log.info("Request is not JSON:");
            throw new RuntimeException("Request is not JSON.");
        }
    }

    public List<String> getTools() {
       return Collections.emptyList();
    }

    protected ArrayNode getToolsNode() {
        if (json == null)
            return null;
        if (json.path("tools").isArray())
            return (ArrayNode) json.path("tools");
        return null;
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

    @Override
    public ObjectNode getJson() {
        return json;
    }

    @Override
    public String getModel() {
        return json.path("model").asText();
    }
}
