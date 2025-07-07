package com.predic8.membrane.core.interceptor.dlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

public class FilterField implements FieldActionStrategy {

    private static final Logger log = LoggerFactory.getLogger(FilterField.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void apply(Message msg, Pattern pattern) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(msg.getBodyAsStringDecoded());
            filterNode(root, pattern, new ArrayDeque<>());
            byte[] out = OBJECT_MAPPER.writeValueAsBytes(root);
            msg.setBodyContent(out);
            msg.getHeader().setContentLength(out.length);
            String originalContentType = msg.getHeader().getContentType();
            if (originalContentType == null || !originalContentType.contains("charset=")) {
                msg.getHeader().setContentType("application/json; charset=UTF-8");
            }
        } catch (Exception e) {
            log.error("FilterAction failed", e);
        }
    }

    private void filterNode(JsonNode node, Pattern pattern, Deque<String> path) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> keys = new ArrayList<>();
            obj.fieldNames().forEachRemaining(keys::add);

            for (String fieldName : keys) {
                path.addLast(fieldName);
                String fullPath = String.join(".", path).toLowerCase(Locale.ROOT);

                if (pattern.matcher(fullPath).matches() || path.stream().anyMatch(p -> pattern.matcher(p.toLowerCase(Locale.ROOT)).matches())) {
                    obj.remove(fieldName);
                    path.removeLast();
                    continue;
                }

                filterNode(obj.get(fieldName), pattern, path);
                path.removeLast();
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                filterNode(child, pattern, path);
            }
        }
    }
}
