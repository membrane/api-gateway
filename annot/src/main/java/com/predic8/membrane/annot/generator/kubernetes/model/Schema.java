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

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.util.*;

import java.util.*;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.OBJECT;

public class Schema extends SchemaObject {

    private final List<ISchema> definitions = new ArrayList<>();

    Schema(String name) {
        super(name);
        type = OBJECT;
    }

    public Schema definition(ISchema definition) {
        definitions.add(definition);
        return this;
    }

    @Override
    public ObjectNode json(ObjectNode node) {
        node.put("$id", "https://membrane-soa.org/%s.schema.json".formatted(name));
        node.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        node.put("title", name);
        super.json(node);
        if (!definitions.isEmpty()) {
            ObjectNode defs = jnf.objectNode();
            for (ISchema def : definitions) {
                defs.put(def.getName(), def.json(jnf.objectNode()));
            }
            node.put("$defs", defs);
        }
        return node;
    }
}
