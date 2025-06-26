package com.predic8.membrane.core.interceptor.dlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

class JsonUtils {

    static void filter(JsonNode node, Pattern p) {
        traverse(node, p, true);
    }

    static void mask(JsonNode node, Pattern p) {
        traverse(node, p, false);
    }

    private static void traverse(JsonNode node, Pattern p, boolean remove) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                if (p.matcher(key).matches()) {
                    if (remove) {
                        it.remove();
                    } else {
                        obj.put(key, "****");
                    }
                } else {
                    traverse(e.getValue(), p, remove);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                traverse(child, p, remove);
            }
        }
    }
}
