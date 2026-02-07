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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.annot.beanregistry.*;

/**
 * Immutable parsing state passed down while traversing YAML.
 * - context: current element scope used for local type resolution in {@link Grammar}.
 * - registry: access to already materialized beans (e.g., for $ref/reference attributes).
 * - grammar: resolves element names to Java classes via local/global lookups.
 */
public class ParsingContext<T extends BeanRegistry & BeanLifecycleManager> {
    private final String context;
    private final T registry;
    private final Grammar grammar;
    private final String path;
    private final JsonNode topLevel;
    private String key;

    public ParsingContext(String context, T registry, Grammar grammar, JsonNode topLevel,  String path) {
        this.context = context;
        this.registry = registry;
        this.grammar = grammar;
        this.path = path;
        this.topLevel = topLevel;
    }

    public ParsingContext<T> updateContext(String context) {
        ParsingContext<T> pc = new ParsingContext<>(context, registry, grammar, topLevel, path);
        pc.key = key;
        return pc;
    }

    public String context() {
        return context;
    }

    public T registry() {
        return registry;
    }

    public Grammar grammar() {
        return grammar;
    }

    public String path() {
        return path;
    }

    public ParsingContext<?> addPath(String path) {
        return new ParsingContext(context, registry, grammar,topLevel, this.path + path);
    }

    public ParsingContext<?> key(String key) {
        var pc = new ParsingContext(context, registry, grammar, topLevel, path);
        pc.key = key;
        return pc;
    }

    public Class<?> resolveClass(String key) {
        Class<?> clazz = grammar.getLocal(context, key);
        if (clazz == null)
            clazz = grammar.getElement(key);
        if (clazz == null) {
            var e = new ConfigurationParsingException("Did not find java class for key '%s'.".formatted(key));
            e.setParsingContext(this);
            throw e;
//            throw new RuntimeException("Did not find java class for key '%s'.".formatted(key));
        }
        return clazz;
    }

    public JsonNode getToplevel() {
         return topLevel;
    }

    public JsonNode getNode() {
        return topLevel;
    }

    public String getKey() {
        return key;
    }
}