package com.predic8.membrane.core.util.json;

import org.jetbrains.annotations.*;
import org.json.*;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.json.JSONObject.NULL;

public class JsonToXml {

    // Prolog is needed to provide the UTF-8 encoding
    private static final String XML_PROLOG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

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

    public String toXmlInternal(String json) {
        Object input = parse(json);
        StringBuilder sb = new StringBuilder();

        // --- Case 1: Single-property object ---
        if (rootName == null &&
            input instanceof JSONObject jsonObj &&
            jsonObj.keySet().size() == 1) {

            String singleKey = jsonObj.keySet().iterator().next();
            sb.append("<").append(singleKey).append(">");
            build(jsonObj.get(singleKey), sb);
            sb.append("</").append(singleKey).append(">");
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

        sb.append("<").append(effectiveRoot).append(">");
        build(input, sb);
        sb.append("</").append(effectiveRoot).append(">");
        return sb.toString();
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

    private static @NotNull Object parseLiteral(String t) {

        // Try numeric types
        if (t.matches("-?\\d+")) return Integer.valueOf(t);
        if (t.matches("-?\\d+\\.\\d+")) return Double.valueOf(t);

        // Check for quoted strings
        if ((t.startsWith("\"") && t.endsWith("\"")) ||
            (t.startsWith("'") && t.endsWith("'")))
            return t.substring(1, t.length() - 1);

        return t;
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
        sb.append(escape(String.valueOf(value)));
    }

    private void buildArray(StringBuilder sb, JSONArray array) {
        startArray(sb);
        buildArrayItems(sb, array);
        endArray(sb);
    }

    private void buildArrayItems(StringBuilder sb, JSONArray array) {
        for (int i = 0; i < array.length(); i++) {
            sb.append("<").append(itemName).append(">");
            build(array.get(i), sb);
            sb.append("</").append(itemName).append(">");
        }
    }

    // Helper for top-level arrays
    private void buildArrayItemsOnly(JSONArray array, StringBuilder sb) {
        buildArrayItems(sb, array);
    }

    private void buildObject(StringBuilder sb, JSONObject jsonObj) {
        for (String key : jsonObj.keySet()) {
            sb.append("<").append(key).append(">");
            build(jsonObj.get(key), sb);
            sb.append("</").append(key).append(">");
        }
    }

    private void endArray(StringBuilder sb) {
        sb.append("</").append(arrayName).append(">");
    }

    private void startArray(StringBuilder sb) {
        sb.append("<").append(arrayName).append(">");
    }

    private String escape(String v) {
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}