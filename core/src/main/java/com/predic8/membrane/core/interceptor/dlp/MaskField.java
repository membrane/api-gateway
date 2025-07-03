package com.predic8.membrane.core.interceptor.dlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Pattern;

public class MaskField implements FieldActionStrategy {

    private static final Logger log = LoggerFactory.getLogger(MaskField.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void apply(Message msg, Pattern pattern) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(msg.getBodyAsStringDecoded());
            maskNode(root, pattern, new ArrayDeque<>());
            byte[] out = OBJECT_MAPPER.writeValueAsBytes(root);
            msg.setBodyContent(out);
            msg.getHeader().setContentLength(out.length);
            String originalContentType = msg.getHeader().getContentType();
            if (originalContentType == null || !originalContentType.contains("charset=")) {
                msg.getHeader().setContentType("application/json; charset=UTF-8");
            }
        } catch (Exception e) {
            log.error("MaskAction failed", e);
        }
    }

    private void maskNode(JsonNode node, Pattern pattern, Deque<String> path) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.fieldNames().forEachRemaining(fn -> {
                path.addLast(fn);
                JsonNode child = obj.get(fn);
                if (child.isValueNode()) {
                    String fullPath = String.join(".", path).toLowerCase(Locale.ROOT);
                    boolean match = pattern.matcher(fullPath).matches()
                            || path.stream().anyMatch(p -> pattern.matcher(p.toLowerCase(Locale.ROOT)).matches());
                    if (match) {
                        obj.put(fn, "****");
                    }
                } else {
                    maskNode(child, pattern, path);
                }
                path.removeLast();
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                path.addLast("[" + i + "]");
                maskNode(node.get(i), pattern, path);
                path.removeLast();
            }
        }
    }
}

