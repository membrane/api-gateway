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

public class SchemaString extends BasicSchema {

    private List<String> enumeration = new ArrayList<>();

    public SchemaString(String name) {
        super(name);
        type("string");
    }

    public SchemaString enumeration(List<String> enumeration) {
        this.enumeration = enumeration;
        return this;
    }

    @Override
    public ObjectNode json(ObjectNode node) {
        super.json(node);
        if (!enumeration.isEmpty()) {
            ArrayNode enumNode = jnf.arrayNode();
            enumeration.forEach(enumNode::add);
            node.set("enum", enumNode);
        }
        return node;
    }
}
