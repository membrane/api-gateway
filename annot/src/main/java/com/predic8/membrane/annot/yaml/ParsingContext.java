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