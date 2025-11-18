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

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.util.*;

public class AnyOf extends SchemaObject {

    private final List<SchemaObject> anyOfs;

    AnyOf(List<SchemaObject> anyOfs) {
        super(null);
        this.anyOfs = anyOfs;
    }

    @Override
    public ObjectNode json(ObjectNode node) {
        return node.set("anyOf", getAnyNode());
    }

    private ArrayNode getAnyNode() {
        ArrayNode list = jnf.arrayNode();
        for (SchemaObject anyOf : anyOfs) {
            list.add(anyOf.json(jnf.objectNode()));
        }
        return list;
    }
}
