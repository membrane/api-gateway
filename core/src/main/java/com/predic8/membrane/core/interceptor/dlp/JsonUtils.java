package com.predic8.membrane.core.interceptor.dlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

class JsonUtils {
    private JsonUtils() {}

    static void removePath(JsonNode root, String dotPath) {
        dotNavigate(root, dotPath, (parent, last) -> ((ObjectNode) parent).remove(last));
    }

    static void maskPath(JsonNode root, String dotPath) {
        dotNavigate(root, dotPath, (parent, last) -> ((ObjectNode) parent).put(last, "****"));
    }

    static void removeKeysMatching(JsonNode node, Pattern p) { traverse(node, p, true); }
    static void maskKeysMatching(JsonNode node, Pattern p)   { traverse(node, p, false); }

    private static void traverse(JsonNode node, Pattern p, boolean remove) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                JsonNode val = e.getValue();
                if (p.matcher(key).matches()) {
                    if (remove) it.remove(); else obj.put(key, "****");
                } else {
                    traverse(val, p, remove);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : (ArrayNode) node) traverse(child, p, remove);
        }
    }

    private interface LeafOp { void apply(ObjectNode parent, String lastSegment); }

    private static void dotNavigate(JsonNode root, String dotPath, LeafOp op) {
        String[] parts = dotPath.split("\\.");
        JsonNode parent = root;
        for (int i = 0; i < parts.length - 1; i++) {
            parent = parent.path(parts[i]);
            if (parent.isMissingNode()) return;
        }
        if (parent.isObject()) op.apply((ObjectNode) parent, parts[parts.length - 1]);
    }
}