/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.yaml.parsing.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.annot.yaml.ParsingContext;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.annot.yaml.McYamlIntrospector.findSetterForKey;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.getElementName;
import static com.predic8.membrane.annot.yaml.NodeValidationUtils.ensureArray;
import static com.predic8.membrane.annot.yaml.NodeValidationUtils.ensureSingleKey;
import static java.lang.reflect.Modifier.isAbstract;
import static java.util.Locale.ROOT;

public final class CollectionBinder {

    private static final ReferenceResolver REFERENCE_RESOLVER = new ReferenceResolver();

    public static List<Object> parseListIncludingStartEvent(ParsingContext<?> pc, JsonNode node, Class<?> elemType) {
        ensureArray(pc, node);
        return parseListExcludingStartEvent(pc, node, elemType);
    }

    public static List<Object> parseListExcludingStartEvent(ParsingContext<?> pc, JsonNode node, Class<?> elemType) {
        List<Object> res = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            res.add(parseListItem(pc.addPath("[%d]".formatted(i)), node.get(i), elemType));
        }
        return res;
    }

    private static Object parseListItem(ParsingContext<?> ctx, JsonNode item, Class<?> elemType) {
        if (item == null || item.isNull())
            throw new ConfigurationParsingException("List items must not be null.");

        if (!item.isObject())
            return parseInlineListItem(ctx, item, elemType);

        JsonNode ref = item.get("$ref");
        if (ref != null) {
            if (item.size() == 1)
                return parseMapToObj(ctx, item);
            throw new ConfigurationParsingException("Cannot mix '$ref' with other fields in a list item.");
        }

        if (item.size() == 1) {
            if (elemType != null) {
                findSetterForKey(elemType, item.fieldNames().next());
                return parseInlineListItem(ctx, item, elemType);
            }
            return parseMapToObj(ctx, item);
        }

        return parseInlineListItem(ctx, item, elemType);
    }

    private static Object parseMapToObj(ParsingContext<?> pc, JsonNode node) {
        ensureSingleKey(pc, node);
        String key = node.fieldNames().next();
        return parseMapToObj(pc, node.get(key), key);
    }

    private static Object parseMapToObj(ParsingContext<?> ctx, JsonNode node, String key) {
        if ("$ref".equals(key))
            return REFERENCE_RESOLVER.resolveReferencedObject(ctx, node.asText(), key);
        var childContext = ctx.addPath("." + key);
        return ObjectBinder.bind(childContext.updateContext(key), childContext.resolveClass(key), node);
    }

    private static Object parseInlineListItem(ParsingContext<?> ctx, JsonNode node, Class<?> elemType) {
        if (elemType == null)
            throw new ConfigurationParsingException("Inline list item form requires a typed list element.");
        if (isScalarElementType(elemType)) {
            if (node.isObject() || node.isArray()) {
                throw new ConfigurationParsingException(
                        "Scalar list item expected for list of %s, but got %s."
                                .formatted(elemType.getSimpleName(), node.getNodeType()));
            }
            return coerceScalarListItem(node, elemType);
        }
        if (elemType.isInterface() || isAbstract(elemType.getModifiers()))
            throw new ConfigurationParsingException("Inline list item form requires a concrete element type, but found: %s.".formatted(elemType.getName()));
        return ObjectBinder.bind(ctx.updateContext(getElementName(elemType)), elemType, node);
    }

    private static boolean isScalarElementType(Class<?> type) {
        return type.isPrimitive()
               || type == String.class
               || type == Boolean.class
               || type == Character.class
               || Number.class.isAssignableFrom(type)
               || type.isEnum();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object coerceScalarListItem(JsonNode node, Class<?> elemType) {
        if (elemType.isEnum()) {
            String raw = node.asText();
            try {
                return Enum.valueOf((Class<? extends Enum>) elemType, raw);
            } catch (IllegalArgumentException ignored) {
            }
            try {
                return Enum.valueOf((Class<? extends Enum>) elemType, raw.toUpperCase(ROOT));
            } catch (IllegalArgumentException e) {
                throw new ConfigurationParsingException(
                        "Invalid value '%s' for enum type %s.".formatted(raw, elemType.getSimpleName()));
            }
        }
        return ScalarValueConverter.convertScalarOrSpel(node, elemType);
    }
}
