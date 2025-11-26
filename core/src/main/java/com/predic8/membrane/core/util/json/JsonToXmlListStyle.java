package com.predic8.membrane.core.util.json;

import org.json.*;

public class JsonToXmlListStyle {

    private String rootName = null;
    private String arrayName = "array";
    private String itemName = "item";

    public void setRootName(String rootName) {
        this.rootName = rootName;
    }

    public void setArrayName(String arrayName) {
        this.arrayName = arrayName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String toXml(String jsonText) {
        Object json = parse(jsonText);
        return toXmlInternal(json);
    }

    public String toXml(Object input) {
        return toXmlInternal(input);
    }

    private String toXmlInternal(Object input) {
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
            sb.append("<").append(arrayName).append(">");
            buildArrayItemsOnly(arr, sb); // <- Important: NO nested array tag here
            sb.append("</").append(arrayName).append(">");
            return sb.toString();
        }

        // --- Case 3: Normal case (object/primitive with root) ---
        String effectiveRoot = rootName != null ? rootName : "root";

        sb.append("<").append(effectiveRoot).append(">");
        build(input, sb);
        sb.append("</").append(effectiveRoot).append(">");
        return sb.toString();
    }

    // Helper for top-level arrays
    private void buildArrayItemsOnly(JSONArray array, StringBuilder sb) {
        for (int i = 0; i < array.length(); i++) {
            sb.append("<").append(itemName).append(">");
            build(array.get(i), sb);
            sb.append("</").append(itemName).append(">");
        }
    }

    private Object parse(String jsonText) {
        String t = jsonText.trim();

        if (t.startsWith("{")) return new JSONObject(t);
        if (t.startsWith("[")) return new JSONArray(t);
        if ("true".equals(t)) return Boolean.TRUE;
        if ("false".equals(t)) return Boolean.FALSE;
        if ("null".equals(t)) return JSONObject.NULL;

        if (t.matches("-?\\d+")) return Integer.valueOf(t);
        if (t.matches("-?\\d+\\.\\d+")) return Double.valueOf(t);

        if ((t.startsWith("\"") && t.endsWith("\"")) ||
            (t.startsWith("'") && t.endsWith("'")))
            return t.substring(1, t.length() - 1);

        return t;
    }

    private void build(Object value, StringBuilder sb) {

        if (value instanceof JSONObject jsonObj) {
            for (String key : jsonObj.keySet()) {
                sb.append("<").append(key).append(">");
                build(jsonObj.get(key), sb);
                sb.append("</").append(key).append(">");
            }
            return;
        }

        if (value instanceof JSONArray array) {
            sb.append("<").append(arrayName).append(">");
            for (int i = 0; i < array.length(); i++) {
                sb.append("<").append(itemName).append(">");
                build(array.get(i), sb);
                sb.append("</").append(itemName).append(">");
            }
            sb.append("</").append(arrayName).append(">");
            return;
        }

        if (value == null || value == JSONObject.NULL) {
            return;
        }

        sb.append(escape(String.valueOf(value)));
    }

    private String escape(String v) {
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}