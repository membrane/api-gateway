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

package com.predic8.membrane.core.util;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

public class JsonUtil {

    protected static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    /**
     * Transforms a scalar value like:
     * - 1 => NumberNode
     * - foo => TextNode
     * - true => BooleNode
     * - null => NullNode
     * into a JsonNode. String are not quoted!
     * @param value String with a JSON scalar value
     * @return Parameter as JsonNode
     */
    public static JsonNode scalarAsJson(String value) {
        if (value == null) return FACTORY.nullNode();
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
                java.math.BigInteger bi = new java.math.BigInteger(value);
                int bl = bi.bitLength();
                if (bl <= 31) return FACTORY.numberNode(bi.intValue());
                if (bl <= 63) return FACTORY.numberNode(bi.longValue());
                return FACTORY.numberNode(bi);
            }
        } catch (NumberFormatException ignore) { /* try decimal */ }

        // decimal?
        try {
            java.math.BigDecimal bd = new java.math.BigDecimal(value);
            // reject NaN/Infinity-equivalents (BigDecimal won’t parse them anyway)
            return FACTORY.numberNode(bd);
        } catch (NumberFormatException ignore) { /* fall through */ }

        return FACTORY.textNode(value);
    }
}
