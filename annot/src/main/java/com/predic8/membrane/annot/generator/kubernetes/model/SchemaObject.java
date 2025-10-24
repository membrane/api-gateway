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

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaUtils.*;
import static java.util.stream.Collectors.*;

public class SchemaObject extends AbstractSchema<SchemaObject> {

    private String description;

    // Java Properties (@MCAttributes, @MCChildElement)
    protected final List<AbstractSchema> properties = new ArrayList<>();

    public SchemaObject() {
        super();
    }

    public SchemaObject(String name) {
        super(name);
    }

    public static SchemaObject string(String name) {
        return new SchemaObject(name).type("string");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (name != null) {
                sb.append("\"").append(name).append("\":");
            }
        sb.append("{");
        String attrs = attributes.entrySet().stream()
                                        .map(SchemaUtils::entryToJson)
                                                          .collect(joining(","));
        sb.append(attrs);
        String props = printProperties();
        if (!props.isEmpty()) {
                if (!attrs.isEmpty()) sb.append(",");
                sb.append(props);
            }
        sb.append("}");
        return sb.toString();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    protected String printProperties() {
        if (properties.isEmpty())
            return "";

        return """
               "properties": {%s} %s""".formatted(getPropertiesJoined(),printRequired(properties));

    }

    private String getPropertiesJoined() {
        return properties.stream().map(AbstractSchema::toString).collect(joining(","));
    }

    public void addProperty(AbstractSchema so) {
        properties.add(so);
    }

     public void setAdditionalProperties(boolean additionalProperties) {
        addAttribute("additionalProperties", additionalProperties);
    }

    public SchemaObject additionalProperties(boolean b) {
        addAttribute("additionalProperties", b);
        return this;
    }

    public SchemaObject required(List required) {
        addAttribute("required", required);
        return this;
    }

    public SchemaObject ref(String ref) {
        addAttribute("$ref", ref);
        return this;
    }

    public SchemaObject enumeration(List enumeration) {
        attribute("enum", enumeration);
        return this;
    }
}
