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

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.*;

public class SchemaObject extends AbstractSchema<SchemaObject> {

    private boolean additionalProperties;

    // Java Properties (@MCAttributes, @MCChildElement)
    protected final List<AbstractSchema<?>> properties = new ArrayList<>();

    private List<AbstractSchema<?>> oneOf;

    private List<AbstractSchema<?>> allOf;
    private final Map<String, AbstractSchema<?>> patternProperties = new LinkedHashMap<>();

    private Integer minProperties;
    private Integer maxProperties;

    SchemaObject(String name) {
        super(name);
        type = OBJECT;
    }

    /**
     * Populates the given {@code ObjectNode} with the JSON schema representation
     * of this {@code SchemaObject}, including additional properties, pattern properties,
     * and combinations (allOf, oneOf).
     *
     * @param node the {@code ObjectNode} to populate with schema details
     * @return the modified {@code ObjectNode} containing the schema representation
     */
    public ObjectNode json(ObjectNode node) {
        super.json(node);

        if (minProperties != null) node.put("minProperties", minProperties);
        if (maxProperties != null) node.put("maxProperties", maxProperties);

        if (!additionalProperties && isObject()) {
            node.put("additionalProperties", false);
        }

        addProperties(node);
        addPatternProperties(node);
        addAllOf(node);
        addOneOf(node);
        return node;
    }

    public SchemaObject property(AbstractSchema<?> as) {
        for (AbstractSchema<?> p : properties)
            if (p.getName().equals(as.getName()))
                throw new IllegalArgumentException("Duplicate property: " + as.getName());
        properties.add(as);
        return this;
    }

    public SchemaObject additionalProperties(boolean additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    public SchemaObject patternProperty(String pattern, AbstractSchema<?> schema) {
        patternProperties.put(pattern, schema);
        return this;
    }

    private void addOneOf(ObjectNode node) {
        if (oneOf == null || oneOf.isEmpty())
            return;

        var oneOfArray = jnf.arrayNode();
        for (AbstractSchema<?> s : oneOf) {
            oneOfArray.add(s.json(jnf.objectNode()));
        }
        node.set("oneOf", oneOfArray);

    }

    private void addAllOf(ObjectNode node) {
        if (allOf == null || allOf.isEmpty())
            return;

        var allOfArray = jnf.arrayNode();
        for (AbstractSchema<?> s : allOf) {
            allOfArray.add(s.json(jnf.objectNode()));
        }
        node.set("allOf", allOfArray);

    }

    private void addPatternProperties(ObjectNode node) {
        if (patternProperties.isEmpty())
            return;

        ObjectNode pp = jnf.objectNode();
        for (var e : patternProperties.entrySet()) {
            pp.set(e.getKey(), e.getValue().json(jnf.objectNode()));
        }
        node.set("patternProperties", pp);
    }

    /**
     * Populates the specified {@code ObjectNode} with the properties of the JSON schema
     * associated with this object. The method iterates over the defined properties, adding
     * them to a "properties" node, and specifies any required properties in a "required" array.
     *
     * @param node the {@code ObjectNode} to populate with the properties and required fields
     */
    private void addProperties(ObjectNode node) {
        if (properties.isEmpty())
            return;

        List<String> required = new ArrayList<>();

        ObjectNode propertiesNode = jnf.objectNode();
        for (AbstractSchema<?> property : properties) {

            propertiesNode.set(property.getName(), createPropertyNode(property));
            if (property.isRequired())
                required.add(property.getName());
        }
        if (!required.isEmpty()) {
            var l = jnf.arrayNode();
            required.forEach(l::add);
            node.set("required", l);
        }
        node.set("properties", propertiesNode);
    }

    private static ObjectNode createPropertyNode(AbstractSchema<?> property) {
        ObjectNode propertyNode = property.json(jnf.objectNode());
        if (property.getEnumValues() != null && !property.getEnumValues().isEmpty()) {
            propertyNode.set("enum", getEnumNode(property));
        }
        return propertyNode;
    }

    private static ArrayNode getEnumNode(AbstractSchema<?> property) {
        var enumValues = jnf.arrayNode();
        property.getEnumValues().forEach(enumValues::add);
        return enumValues;
    }

    @Override
    public boolean isObject() {
        return true;
    }

    public SchemaObject oneOf(List<AbstractSchema<?>> oneOf) {
        this.oneOf = oneOf;
        return this;
    }

    public SchemaObject allOf(List<AbstractSchema<?>> allOf) {
        this.allOf = allOf;
        return this;
    }

    public SchemaObject minProperties(int n) { this.minProperties = n; return this; }
    public SchemaObject maxProperties(int n) { this.maxProperties = n; return this; }

    public boolean hasProperty(String name) {
        return properties.stream().anyMatch(p -> name.equals(p.getName()));
    }
}