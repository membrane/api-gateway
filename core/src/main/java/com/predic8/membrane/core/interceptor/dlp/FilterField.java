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
    private static final ObjectMapper M = new ObjectMapper();

    @Override
    public void apply(Message msg, Pattern pattern) {
        try {
            JsonNode root = M.readTree(msg.getBodyAsStringDecoded());
            filterNode(null, root, pattern, new ArrayDeque<>());
            byte[] out = M.writeValueAsBytes(root);
            msg.setBodyContent(out);
            msg.getHeader().setContentLength(out.length);
            msg.getHeader().setContentType("application/json; charset=UTF-8");
        } catch (Exception e) {
            log.error("FilterAction failed", e);
        }
    }

    private void filterNode(ObjectNode parent, JsonNode node, Pattern pattern, Deque<String> path) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> keys = new ArrayList<>();
            obj.fieldNames().forEachRemaining(keys::add);

            for (String fn : keys) {
                path.addLast(fn);
                String fullPath = String.join(".", path).toLowerCase(Locale.ROOT);
                boolean match = pattern.matcher(fullPath).matches()
                        || path.stream().anyMatch(p -> pattern.matcher(p.toLowerCase(Locale.ROOT)).matches());

                if (match) {
                    obj.remove(fn);
                    path.removeLast();
                    continue;
                }

                filterNode(obj, obj.get(fn), pattern, path);
                path.removeLast();
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                filterNode(parent, child, pattern, path);
            }
        }
    }
}
