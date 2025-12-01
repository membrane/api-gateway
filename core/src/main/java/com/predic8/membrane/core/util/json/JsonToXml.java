package com.predic8.membrane.core.util.json;

import org.jetbrains.annotations.*;
import org.json.*;

import static java.lang.Boolean.*;
import static org.json.JSONObject.*;

public class JsonToXml {

    // Prolog is needed to provide the UTF-8 encoding
    static final String XML_PROLOG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    private String rootName = null;
    private String arrayName = "array";
    private String itemName = "item";

    public JsonToXml rootName(String rootName) {
        this.rootName = rootName;
        return this;
    }

    public JsonToXml arrayName(String arrayName) {
        this.arrayName = arrayName;
        return this;
    }

    public JsonToXml itemName(String itemName) {
        this.itemName = itemName;
        return this;
    }

    public String toXml(String json) {
        return XML_PROLOG + toXmlInternal(json);
    }

    String toXmlInternal(String json) {
        Object input = parse(json);
        StringBuilder sb = new StringBuilder();

        // --- Case 1: Single-property object ---
        if (rootName == null &&
            input instanceof JSONObject jsonObj &&
            jsonObj.keySet().size() == 1) {

            String singleKey = jsonObj.keySet().iterator().next();
            startTag(sb, singleKey);
            build(jsonObj.get(singleKey), sb);
            endTag(sb, singleKey);
            return sb.toString();
        }

        // --- Case 2: Top-level array without explicit root ---
        if (rootName == null && input instanceof JSONArray arr) {
            startArray(sb);
            buildArrayItemsOnly(arr, sb); // <- Important: NO nested array tag here
            endArray(sb);
            return sb.toString();
        }

        // --- Case 3: Normal case (object/primitive with root) ---
        String effectiveRoot = rootName != null ? rootName : "root";

        startTag(sb, effectiveRoot);
        build(input, sb);
        endTag(sb, effectiveRoot);
        return sb.toString();
    }

    private static void endTag(StringBuilder sb, String singleKey) {
        sb.append("</").append(sanitizeXmlName( singleKey)).append(">");
    }

    private static void startTag(StringBuilder sb, String singleKey) {
        sb.append("<").append(sanitizeXmlName( singleKey)).append(">");
    }

    static @NotNull Object parseLiteral(String t) {

        // Try numeric types first (Double first to avoid Long overflow)
        if (t.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) {
            try {
                double d = Double.parseDouble(t);

                // If integer without decimal/exponent → try Long if it fits
                if (t.matches("-?\\d+")) {
                    try {
                        return Long.parseLong(t);
                    } catch (NumberFormatException ignored) {
                        // too large → keep as Double
                    }
                }

                return d;

            } catch (NumberFormatException e) {
                // fallback: treat as string
                return t;
            }
        }

        // Check for quoted strings
        if (t.startsWith("\"") && t.endsWith("\""))
            return t.substring(1, t.length() - 1);

        return t;
    }


    private Object parse(String jsonText) {
        String t = jsonText.trim();

        if (t.startsWith("{")) return new JSONObject(t);
        if (t.startsWith("[")) return new JSONArray(t);
        return switch (t) {
            case "true" -> TRUE;
            case "false" -> FALSE;
            case "null" -> NULL;
            default -> parseLiteral(t);

        };
    }

    private void build(Object value, StringBuilder sb) {

        if (value instanceof JSONObject jsonObj) {
            buildObject(sb, jsonObj);
            return;
        }

        if (value instanceof JSONArray array) {
            buildArray(sb, array);
            return;
        }

        if (value == null || value == NULL) {
            return;
        }
        sb.append(escapeTextContent(String.valueOf(value)));
    }

    private void buildArray(StringBuilder sb, JSONArray array) {
        startArray(sb);
        buildArrayItems(sb, array);
        endArray(sb);
    }

    private void buildArrayItems(StringBuilder sb, JSONArray array) {
        for (int i = 0; i < array.length(); i++) {
            startTag(sb, itemName);
            build(array.get(i), sb);
            endTag(sb, itemName);
        }
    }

    private static String sanitizeXmlName(String key) {

        // Replace any illegal XML characters
        String sanitized = key.replaceAll("[^A-Za-z0-9_.-]", "_");

        // XML element must NOT start with number or dot or hyphen
        if (!sanitized.matches("[A-Za-z_].*")) {
            sanitized = "_" + sanitized;
        }

        return sanitized;
    }


    // Helper for top-level arrays
    private void buildArrayItemsOnly(JSONArray array, StringBuilder sb) {
        buildArrayItems(sb, array);
    }

    private void buildObject(StringBuilder sb, JSONObject jsonObj) {
        for (String key : jsonObj.keySet()) {
            startTag(sb, key);
            build(jsonObj.get(key), sb);
            endTag(sb, key);
        }
    }

    private void endArray(StringBuilder sb) {
        endTag(sb, arrayName);
    }

    private void startArray(StringBuilder sb) {
        startTag(sb, arrayName);
    }

    /**
     * Not suitable for attribute values cause " and ' must be escaped there
     */
    private String escapeTextContent(String v) {
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}