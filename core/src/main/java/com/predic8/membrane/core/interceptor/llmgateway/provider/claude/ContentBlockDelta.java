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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ContentBlockDelta {

    private int index;
    private String deltaType;
    private String partialJson;

    public static ContentBlockDelta from(ObjectNode on) {
        var cbd = new ContentBlockDelta();

        cbd.index = on.path("index").asInt();

        JsonNode delta = on.path("delta");
        cbd.deltaType = delta.path("type").asText(null);
        cbd.partialJson = delta.path("partial_json").asText("");

        return cbd;
    }

    public boolean isInputJsonDelta() {
        return "input_json_delta".equals(deltaType);
    }

    public int getIndex() {
        return index;
    }

    public String getDeltaType() {
        return deltaType;
    }

    public String getPartialJson() {
        return partialJson;
    }
}
