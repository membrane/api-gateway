/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static java.util.Optional.empty;

public class JsonUtil {

    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);


    private static final ObjectMapper om = new ObjectMapper();

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    /**
     * Transforms a scalar value like:
     * - 1 => NumberNode
     * - foo => TextNode
     * - true => BooleanNode
     * - null => NullNode
     * into a JsonNode. Strings are not quoted!
     *
     * @param value String with a JSON scalar value
     * @return Parameter as JsonNode
     */
    public static JsonNode scalarAsJson(String value) {
        if (value == null) return FACTORY.nullNode();

        // Be lenient towards accidental whitespace in query strings
        value = value.trim();
        switch (value) {
            case "true" -> {
                return FACTORY.booleanNode(true);
            }
            case "false" -> {
                return FACTORY.booleanNode(false);
            }
            case "null" -> {
                return FACTORY.nullNode();
            }
        }

        // integer?
        try {
            if (!value.contains(".") && !value.contains("e") && !value.contains("E")) {
                BigInteger bi = new BigInteger(value);
                if (bi.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                    && bi.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
                    return FACTORY.numberNode(bi.intValue());
                }
                if (bi.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                    && bi.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                    return FACTORY.numberNode(bi.longValue());
                }
                return FACTORY.numberNode(bi);
            }
        } catch (NumberFormatException ignore) { /* try decimal */ }

        // decimal?
        try {
            // reject NaN/Infinity-equivalents (BigDecimal won’t parse them anyway)
            return FACTORY.numberNode(new BigDecimal(value));
        } catch (NumberFormatException ignore) { /* fall through */ }

        return FACTORY.textNode(value);
    }

    public static Optional<ObjectNode> getJsonObject(Message msg) {
        try {
            JsonNode jsonNode = om.readTree(msg.getBodyAsStreamDecoded());
            if (jsonNode instanceof ObjectNode on) {
                return Optional.of(on);
            }
            log.debug("Expected JSON Object but got: {}",jsonNode.getNodeType());
        } catch (IOException e) {
            log.debug("Error reading JSON: {}", e.getMessage());
        }
        return empty();
    }

    public static void setJsonBody(Message msg, ObjectNode json) {
        try {
            if (!msg.isJSON()) {
                msg.getHeader().setContentType(APPLICATION_JSON);
            }
            msg.setBodyContent(om.writeValueAsBytes(json));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
