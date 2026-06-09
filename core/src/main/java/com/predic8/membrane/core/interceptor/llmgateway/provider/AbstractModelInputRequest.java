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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.multipart.MultipartUtil;
import com.predic8.membrane.core.util.json.JsonUtil;
import jakarta.mail.internet.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AbstractModelInputRequest extends BaseLLMRequest implements ModelInputRequest {

    private static final Logger log = LoggerFactory.getLogger(AbstractModelInputRequest.class);

    private static final ObjectMapper om = new ObjectMapper();

    protected ObjectNode json;

    private String model;

    private AbstractBody body;

    public AbstractModelInputRequest(Exchange exchange) throws IOException {
        super(exchange);

        if (exchange.getRequest().getHeader().isMultipart()) {
            try {
                for (var part : MultipartUtil.split(exchange.getRequest())) {
                    log.info("Part: name={} type={} size={}", part.getName(), part.getContentType(), part.getBody().length);
                    if ("model".equals(part.getName())) {
                        log.info("Model: {}", part.getBodyAsString());
                        model = part.getBodyAsString();
                    }
                }
                body = exchange.getRequest().getBody();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        if (exchange.getRequest().isJSON()) {
            json = JsonUtil.getJsonObject(exchange.getRequest()).orElseThrow(() -> new RuntimeException("Cannot parse input as JSON message."));
        }

        if (json != null) {
            if (json.has("model")) {
                model = json.path("model").asText();
            }
        }
    }

    public List<String> getTools() {
       return Collections.emptyList();
    }

    @Override
    public String getSystemPrompt() {
        return "";
    }

    @Override
    public void setSystemPrompts(List<String> prompts) {
        log.warn("Not supported.");
    }

    @Override
    public void removeSystemPrompt() {
        log.warn("Not supported.");
    }

    protected ArrayNode getToolsNode() {
        if (json == null)
            return null;
        if (json.path("tools").isArray())
            return (ArrayNode) json.path("tools");
        return null;
    }


    @Override
    public ObjectNode getJson() {
        return json;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public long getRequestedMaxOutputTokens() {
        return -1;
    }

    @Override
    public void setMaxOutputTokens(int maxOutputTokens) {
        log.warn("Not supported.");
    }

    @Override
    public long estimateInputTokens() {
        return 0;
    }

    @Override
    public AbstractBody getBody() {
        if (body != null)
            return body;
        try {
            return new Body(om
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(json).getBytes(UTF_8));
        } catch (JsonProcessingException e) {
            log.info("Could not serialize JSON: {}", e.getMessage());
            throw new RuntimeException("Could not serialize JSON: " + e.getMessage());
        }
    }
}
