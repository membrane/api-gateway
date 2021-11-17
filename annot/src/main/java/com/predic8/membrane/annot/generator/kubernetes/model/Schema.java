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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Schema implements ISchema {

    private final String name;

    private final List<SchemaObject> definitions = new ArrayList<>();
    private final List<SchemaObject> properties = new ArrayList<>();

    public Schema(String name) {
        this.name = name;
    }

    public void addDefinition(SchemaObject definition) {
        definitions.add(definition);
    }

    @Override
    public void addProperty(SchemaObject property) {
        properties.add(property);
    }

    private String printDefinitions() {
        if (definitions.isEmpty())
            return "";

        return "\"definitions\":{" + definitions.stream()
                .map(Objects::toString)
                .collect(Collectors.joining(",")) +
                "}";
    }

    private String printProperties() {
        if (properties.isEmpty())
            return "";

        return "\"properties\":{\"spec\":{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{" +
                properties.stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining(",")) +
                "}}}";
    }

    private String checkTrailingCommaNeed() {
        boolean hasDefs = !definitions.isEmpty();
        boolean hasProps = !properties.isEmpty();
        return ((hasDefs && !hasProps) || (!hasDefs && hasProps)) ? "" : ",";
    }

    private String printRequired() {
        String required = properties.stream()
                .filter(SchemaObject::isRequired)
                .map(so -> "\"" + so.getName() + "\"")
                .collect(Collectors.joining(","));

        if (required.isEmpty())
            return "";

        return ",\"required\":[" + required + "]";
    }

    @Override
    public String toString() {
        return "{" +
                "\"id\": \"https://membrane-soa.org/" + name.toLowerCase() + ".schema.json\"," +
                "\"$schema\": \"https://json-schema.org/draft-04/schema#\"," +
                "\"title\": \"" + name + "\"," +
                "\"type\": \"object\"," +
                printDefinitions() +
                checkTrailingCommaNeed() +
                printProperties() +
                printRequired() +
                "}"
                ;
    }
}
