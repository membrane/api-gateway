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
     * @return
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
