/* Copyright 2025 predic8 GmbH, www.predic8.com

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

public abstract class AbstractSchema<T extends AbstractSchema<T>> implements ISchema {

    protected static final JsonNodeFactory jnf = JsonNodeFactory.instance;

    protected String name;
    protected String type;
    protected String description;
    private List<String> enumValues;

    protected boolean required = false;

    public AbstractSchema() {}

    public AbstractSchema(String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    @Override
    public String getName() {
        return name;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public T description(String description) {
        this.description = description;
        return self();
    }

    public boolean isRequired() {
        return required;
    }

    public T required(boolean b) {
        required = b;
        return self();
    }

    public T name(String name) {
        this.name = name;
        return self();
    }

    public T type(String type) {
        this.type = type;
        return self();
    }

    public T enumValues(List<String> enumValues) {
        this.enumValues = enumValues;
        return self();
    }

    public boolean isObject() {
        return "object".equals(type);
    }

    @Override
    public ObjectNode json(ObjectNode node) {
        if (type != null)
            node.put("type", type);
        return node;
    }
}