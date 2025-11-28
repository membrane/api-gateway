/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.grease.strategies;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.predic8.membrane.core.http.MimeType.isJson;

@MCElement(name = "greaseJson", topLevel = false)
public class JsonGrease extends Greaser {

    private static final ObjectMapper om = JsonMapper.builder().build();
    private static final Logger log = LoggerFactory.getLogger(JsonGrease.class);

    boolean shuffleFields = true;
    boolean additionalProperties = true;

    @Override
    protected Message process(Message msg) {
        try {
            ObjectNode json = (ObjectNode) om.readTree(msg.getBody().getContentAsStream());
            if (additionalProperties) {
                processJson(json, JsonGrease::injectField);
            }
            if (shuffleFields) {
                processJson(json, JsonGrease::shuffleNodeFields);
            }
            msg.setBodyContent(om.writeValueAsBytes(json));

            return msg;
        } catch (IOException e) {
            log.info("Failed to read JSON body ", e);
            return msg;
        }
    }

    @Override
    protected boolean isApplicable(Message msg) {
        return isJson(msg.getHeader().getContentType());
    }

    @Override
    protected String getGreaseChanges() {
        return (shuffleFields ? "JSON fields shuffled" : "") +
                (shuffleFields && additionalProperties ? ", " : "") +
                (additionalProperties ? "Added random JSON fields" : "");
    }

    static private void injectField(ObjectNode node) {
        node.put("grease", "Field added by Membrane's Grease plugin");
    }

    static void processJson(ObjectNode jsonNode, Consumer<ObjectNode> action) {
        action.accept(jsonNode);
        jsonNode.propertyNames().forEach(fieldName -> {
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
        fieldsOrdered.addAll(objectNode.propertyNames());
        List<String> fields = new ArrayList<>(fieldsOrdered);
        while (fields.equals(fieldsOrdered)) {
            Collections.shuffle(fields);
        }
        ObjectNode shuffledNode = om.createObjectNode();
        for (String field : fields) {
            shuffledNode.set(field, objectNode.get(field));
        }
        objectNode.removeAll();
        objectNode.setAll(shuffledNode);
    }

    @MCAttribute
    public void setAdditionalProperties(boolean additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    @MCAttribute
    public void setShuffleFields(boolean shuffleFields) {
        this.shuffleFields = shuffleFields;
    }

    @SuppressWarnings("unused")
    public boolean isAdditionalProperties() {
        return additionalProperties;
    }

    @SuppressWarnings("unused")
    public boolean isShuffleFields() {
        return shuffleFields;
    }
}
