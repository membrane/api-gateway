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

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaUtils.*;

public class SchemaObject implements ISchema {

    private final String name;
    private boolean required;

    private String description;

    // Properties to be copied 1:1 to the JSON schema, e.g. "type": "string"
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    // Java Properties (@MCAttributes, @MCChildElement)
    private final List<SchemaObject> properties = new ArrayList<>();

    public SchemaObject(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        if (name != null) {
            sb.append("\"")
                    .append(name)
                    .append("\":");
        }

        sb.append("{")
                .append(attributes.entrySet().stream()
                       .map(SchemaUtils::entryToJson)
                       .collect(Collectors.joining(",")) +
               printProperties())
                .append("}");
        return sb.toString();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    private String printProperties() {
        if (properties.isEmpty())
            return "";

        return ",\"properties\": {" +
               properties.stream().map(SchemaObject::toString).collect(Collectors.joining(",")) +
               "}" +
               printRequired(properties)
                ;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    public void addProperty(SchemaObject so) {
        properties.add(so);
    }

    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @Override
    public void setAdditionalProperties(boolean additionalProperties) {
        addAttribute("additionalProperties", additionalProperties);
    }
}
