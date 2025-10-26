/* Copyright 2009, 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot.generator.kubernetes.model;

import com.fasterxml.jackson.databind.node.*;

import java.util.*;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.OBJECT;

public class SchemaObject extends AbstractSchema<SchemaObject> {

    private String ref;
    private boolean additionalProperties;

    // Java Properties (@MCAttributes, @MCChildElement)
    protected final List<AbstractSchema> properties = new ArrayList<>();

    SchemaObject(String name) {
        super(name);
        type = OBJECT;
    }

    public SchemaObject property(AbstractSchema as) {
        properties.add(as);
        return this;
    }

    public SchemaObject additionalProperties(boolean additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    public ObjectNode json(ObjectNode node) {
        super.json(node);

        if (ref != null) {
            node.put("$ref", ref);
        }

        if (!additionalProperties && isObject()) {
            node.put("additionalProperties", false);
        }

        jsonProperties(node);

        return node;
    }

    private void jsonProperties(ObjectNode node) {
        List<String> required = new ArrayList<>();
        if (!properties.isEmpty()) {
            ObjectNode propertiesNode = jnf.objectNode();
            for (AbstractSchema property : properties) {
                propertiesNode.put(property.getName(), property.json(jnf.objectNode()));
                if (property.isRequired())
                    required.add(property.getName());
            }
            if (!required.isEmpty()) {
                var l = jnf.arrayNode();
                required.forEach(l::add);
                node.put("required",l);
            }
            node.put("properties", propertiesNode);
        }
    }
}