/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.openapi.util;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.io.*;
import java.math.*;
import java.util.*;

public class JsonUtil {

    public static final ObjectMapper om = new ObjectMapper();

    public static JsonNode mapToJson(Object m) {
        return om.convertValue(m, JsonNode.class);
    }

    public static JsonNode getNumbers(String name, BigDecimal n) {
        ObjectNode root = om.createObjectNode();
        root.put(name,n);
        return root;
    }

    public static JsonNode getStrings(String name, String value) {
        ObjectNode root = om.createObjectNode();
        root.put(name,value);
        return root;
    }

    public static byte[] convert2JSON(Map<String, Object> customer) throws IOException {
        return om.writer().writeValueAsBytes(om.valueToTree(customer));
    }
}
