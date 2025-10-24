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
import java.util.stream.Collectors;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaUtils.printRequired;

public class Schema extends SchemaObject {

    private final String name;

    private final List<ISchema> definitions = new ArrayList<>();

    public Schema(String name) {
        this.name = name;
    }

    public void addDefinition(ISchema definition) {
        definitions.add(definition);
    }

    private String printDefinitions() {
        if (definitions.isEmpty())
            return "";

        return ",\"$defs\":{" + definitions.stream()
                .map(Objects::toString)
                .collect(Collectors.joining(",")) +
                "}";
    }

    @Override
    public String toString() {
        return "{" +
                "\"$id\": \"https://membrane-soa.org/" + name.toLowerCase() + ".schema.json\"," +
                "\"$schema\": \"https://json-schema.org/draft/2020-12/schema\"," +
                "\"title\": \"" + name + "\"," +
                "\"type\": \"object\"" +
                "," +
               printProperties() +
               printDefinitions() +
               printRequired(properties)
                + (!attributes.isEmpty() ? "," : "") +
                attributes.entrySet().stream()
                        .map(SchemaUtils::entryToJson)
                        .collect(Collectors.joining(",")) +
                "}"
                ;
    }
}
