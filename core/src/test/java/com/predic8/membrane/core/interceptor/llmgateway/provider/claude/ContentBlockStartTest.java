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
import com.predic8.membrane.core.util.json.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContentBlockStartTest {

    @Test
    void toolUseBlockYieldsTheToolName() {
        var cbs = ContentBlockStart.from(json("""
                {"type":"content_block_start","index":1,
                 "content_block":{"type":"tool_use","id":"tu_1","name":"get_weather"}}"""));

        assertEquals("get_weather", cbs.getToolUse().getName());
    }

    @Test
    void textBlockHasNoToolUse() {
        var cbs = ContentBlockStart.from(json("""
                {"type":"content_block_start","index":0,
                 "content_block":{"type":"text","text":""}}"""));

        assertNull(cbs.getToolUse());
    }

    @Test
    void eventWithoutContentBlockHasNoToolUse() {
        assertNull(ContentBlockStart.from(json("""
                {"type":"content_block_start","index":0}""")).getToolUse());
    }

    @Test
    void nullContentBlockHasNoToolUse() {
        assertNull(ContentBlockStart.from(json("""
                {"type":"content_block_start","content_block":null}""")).getToolUse());
    }

    private static ObjectNode json(String json) {
        return JsonUtil.getJsonObject(json).orElseThrow();
    }
}
