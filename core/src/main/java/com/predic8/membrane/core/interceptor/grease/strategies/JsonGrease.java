package com.predic8.membrane.core.interceptor.grease.strategies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.MimeType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;

@MCElement(name = "json", topLevel = false)
public class JsonGrease implements GreaseStrategy {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Body apply(Body body) {
        try {
            return new Body(
                    objectMapper.writeValueAsBytes(
                            shuffleJson(objectMapper.readTree(body.getContentAsStream()))
                    )
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getApplicableContentType() {
        return APPLICATION_JSON;
    }

    static JsonNode shuffleJson(JsonNode node) {
        if (node.isObject()) {
            return shuffleObject((ObjectNode) node);
        } else if (node.isArray()) {
            return shuffleArrayObjects((ArrayNode) node);
        }
        return node;
    }

    static ObjectNode shuffleObject(ObjectNode objectNode) {
        List<String> fieldsOrdered = new ArrayList<>();
        objectNode.fieldNames().forEachRemaining(fieldsOrdered::add);
        List<String> fields = new ArrayList<>(fieldsOrdered);
        while (fields.equals(fieldsOrdered)) {
            Collections.shuffle(fields);
        }
        ObjectNode shuffledNode = objectMapper.createObjectNode();
        for (String field : fields) {
            shuffledNode.set(field, shuffleJson(objectNode.get(field)));
        }
        return shuffledNode;
    }

    static ArrayNode shuffleArrayObjects(ArrayNode arrayNode) {
        ArrayNode shuffledArray = objectMapper.createArrayNode();
        arrayNode.forEach(element -> shuffledArray.add(shuffleJson(element)));
        return shuffledArray;
    }
}