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
package com.predic8.membrane.core.interceptor.llmgateway.provider.claude;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The {@code content_block_delta} event, one piece of a content block. The arguments of a tool call
 * arrive as {@code input_json_delta} fragments that only make up a JSON document together.
 */
public record ContentBlockDelta(int index, String deltaType, String partialJson) {

    public static ContentBlockDelta from(ObjectNode on) {
        var delta = on.path("delta");
        return new ContentBlockDelta(
                on.path("index").asInt(),
                delta.path("type").asText(null),
                delta.path("partial_json").asText(""));
    }

    public boolean isInputJsonDelta() {
        return "input_json_delta".equals(deltaType);
    }
}
