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

package com.predic8.membrane.annot.yaml;

import com.predic8.membrane.annot.*;

/**
 * Immutable parsing state passed down while traversing YAML.
 * - context: current element scope used for local type resolution in {@link Grammar}.
 * - registry: access to already materialized beans (e.g., for $ref/reference attributes).
 * - grammar: resolves element names to Java classes via local/global lookups.
 */
public record ParsingContext(String context, BeanRegistry registry, Grammar grammar) {

    ParsingContext updateContext(String context) {
        return new ParsingContext(context, registry, grammar);
    }

    public Class<?> resolveClass(String key) {
        Class<?> clazz = grammar.getLocal(context, key);
        if (clazz == null)
            clazz = grammar.getElement(key);
        if (clazz == null)
            throw new RuntimeException("Did not find java class for key '%s'.".formatted(key));
        return clazz;
    }
}