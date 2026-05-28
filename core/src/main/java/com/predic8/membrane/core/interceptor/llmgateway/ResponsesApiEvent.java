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

package com.predic8.membrane.core.interceptor.llmgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponsesApiEvent extends AbstractLLMEvent {

    private static final Logger log = LoggerFactory.getLogger(ResponsesApiEvent.class);

    private final String type;

    public ResponsesApiEvent(JsonNode json) {
        super(json);

        this.type = json.path("type").asText();

        log.debug("Responses API event: {}", type);

        if ("response.output_item.done".equals(type)) {

            var item = json.path("item");

            if (item.isObject()) {
                var on = (ObjectNode) item;

                if ("function_call".equals(on.path("type").asText())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Function call: {} with params {}",
                                on.path("name").asText(),
                                on.path("arguments").asText());
                    } else {
                        log.info("Function call: {}", on.path("name"));
                    }
                }
            }
        }
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "ResponsesApiEvent{" +
                "type='" + type + '\'' +
                '}';
    }
}
