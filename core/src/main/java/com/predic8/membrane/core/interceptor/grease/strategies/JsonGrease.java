package com.predic8.membrane.core.interceptor.grease.strategies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON_UTF8;

@MCElement(name = "json", topLevel = false)
public class JsonGrease extends GreaseStrategy {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(GreaseStrategy.class);

    boolean shuffleFields = true;
    boolean addAdditionalFields = true;

    public JsonGrease() {
        contentTypes = List.of(APPLICATION_JSON, APPLICATION_JSON_UTF8);
    }

    @Override
    public Message apply(Message msg) {
        try {
            ObjectNode json = (ObjectNode) objectMapper.readTree(msg.getBody().getContentAsStream());
            if (addAdditionalFields) {
                processJson(json, JsonGrease::injectField);
            }
            if (shuffleFields) {
                processJson(json, JsonGrease::shuffleNodeFields);
            }
            msg.setBody(new Body(objectMapper.writeValueAsBytes(json)));
            return msg;
        } catch (IOException e) {
            log.info("Failed to read JSON body ", e);
            return msg;
        }
    }

    @Override
    public String getGreaseChanges() {
        return (shuffleFields ? "JSON fields shuffled" : "") +
                (shuffleFields && addAdditionalFields ? ", " : "") +
                (addAdditionalFields ? "Added random JSON fields" : "");
    }

    static private void injectField(ObjectNode node) {
        node.put("grease", "Field added by Membrane's Grease plugin");
    }

    static void processJson(ObjectNode jsonNode, Consumer<ObjectNode> action) {
        action.accept(jsonNode);
        jsonNode.fieldNames().forEachRemaining(fieldName -> {
            JsonNode childNode = jsonNode.get(fieldName);
            if (childNode.isObject()) {
                processJson((ObjectNode) childNode, action);
            } else if (childNode.isArray()) {
                ArrayNode arrayNode = (ArrayNode) childNode;
                arrayNode.forEach(arrayElement -> {
                    if (arrayElement.isObject()) {
                        processJson((ObjectNode) arrayElement, action);
                    }
                });
            }
        });
    }

    static void shuffleNodeFields(ObjectNode objectNode) {
        List<String> fieldsOrdered = new ArrayList<>();
        objectNode.fieldNames().forEachRemaining(fieldsOrdered::add);
        List<String> fields = new ArrayList<>(fieldsOrdered);
        while (fields.equals(fieldsOrdered)) {
            Collections.shuffle(fields);
        }
        ObjectNode shuffledNode = objectMapper.createObjectNode();
        for (String field : fields) {
            shuffledNode.set(field, objectNode.get(field));
        }
        objectNode.removeAll();
        objectNode.setAll(shuffledNode);
    }

    @MCAttribute
    public void setAddAdditionalFields(boolean addAdditionalFields) {
        this.addAdditionalFields = addAdditionalFields;
    }

    @MCAttribute
    public void setShuffleFields(boolean shuffleFields) {
        this.shuffleFields = shuffleFields;
    }

    @SuppressWarnings("unused")
    public boolean isAddAdditionalFields() {
        return addAdditionalFields;
    }

    @SuppressWarnings("unused")
    public boolean isShuffleFields() {
        return shuffleFields;
    }
}
