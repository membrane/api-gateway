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

import java.io.*;
import java.math.*;

import static com.predic8.membrane.core.util.JsonUtil.JsonType.*;
import static com.predic8.membrane.core.util.JsonUtil.JsonType.NULL;
import static com.predic8.membrane.core.util.JsonUtil.JsonType.NUMBER;
import static com.predic8.membrane.core.util.JsonUtil.JsonType.UNKNOWN;
import static java.lang.Character.isDigit;

public class JsonUtil {

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

    public enum JsonType {
        OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL, UNKNOWN
    }

    /**
     * Detects the JSON type of the first non-whitespace character in the stream.
     * Stream is consumed and cannot be read again!
     * @param in
     * @return
     * @throws IOException
     */
    public static JsonType detectJsonType(InputStream in) throws IOException {

        int b;
        do {
            b = in.read();
        } while (b != -1 && Character.isWhitespace(b));

        if (b == -1) {
            return UNKNOWN;
        }

        char c = (char) b;

        return switch (c) {
            case '{' -> OBJECT;
            case '[' -> ARRAY;
            case '"' -> STRING; // true
            case 't', 'f' -> BOOLEAN; // false
            case 'n' -> NULL; // null
            default -> {
                // number test (very loose)
                if (isDigit(c) || c == '-' || c == '+') {
                    yield NUMBER;
                }
                yield UNKNOWN;
            }
        };
    }

}
