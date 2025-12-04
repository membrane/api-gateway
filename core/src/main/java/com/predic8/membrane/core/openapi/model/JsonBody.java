/*
 *  Copyright 2017 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.model;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import java.io.IOException;

public class JsonBody implements Body {

    private static final ObjectReader m = new ObjectMapper().reader();

    private final JsonNode payload;

    public JsonBody(JsonNode s) {
        payload=s;
    }

    public JsonBody(String s) throws JacksonException {
        payload = m.readTree(s);
    }

    public JsonNode getPayload() {
        return payload;
    }

    @Override
    public String asString() {
        return payload.toString();
    }

    @Override
    public JsonNode getJson() throws JacksonException {
        return payload;
    }
}
