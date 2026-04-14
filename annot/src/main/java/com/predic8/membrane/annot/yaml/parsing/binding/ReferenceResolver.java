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

import static com.predic8.membrane.annot.yaml.McYamlIntrospector.getChildSetter;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.getElementName;
import static com.predic8.membrane.annot.yaml.NodeValidationUtils.ensureTextual;

public final class ReferenceResolver {

    public <T> void applyObjectLevelRef(ParsingContext<?> ctx, Class<T> parentClass, JsonNode parentNode, JsonNode refNode, T obj) {
        ensureTextual(refNode, "Expected a string after the '$ref' key.");
        Object referenced = resolveReferencedObject(ctx, refNode.asText(), "$ref");
        String refKey = getElementName(referenced.getClass());

        if (parentNode.has(refKey)) {
            throw new ConfigurationParsingException("Cannot use '$ref' together with inline '%s' in '%s'."
                    .formatted(refKey, ctx.getContext()));
        }

        try {
            getChildSetter(parentClass, referenced.getClass()).invoke(obj, referenced);
        } catch (RuntimeException e) {
            throw new ConfigurationParsingException(
                    "Referenced component '%s' (type '%s') is not allowed in '%s'."
                            .formatted(refNode.asText(), refKey, ctx.getContext()), e, ctx.key("$ref"));
        } catch (Throwable t) {
            throw new ConfigurationParsingException(t);
        }
    }

    public Object resolveReference(ParsingContext<?> ctx, String ref, String key, Class<?> wanted) {
        Object resolved = resolveReferencedObject(ctx, ref, key);
        if (!wanted.isAssignableFrom(resolved.getClass())) {
            throw new ConfigurationParsingException(
                    "Referenced bean '%s' has type '%s' but '%s' expects '%s'."
                            .formatted(ref, resolved.getClass().getName(), key, wanted.getName())
            );
        }
        return resolved;
    }

    public Object resolveReferencedObject(ParsingContext<?> ctx, String ref, String key) {
        final Object resolved;
        try {
            resolved = ctx.getRegistry().resolve(ref);
        } catch (RuntimeException e) {
            throw new ConfigurationParsingException("Cannot resolve reference: " + ref, e, ctx.key(key));
        }
        if (resolved == null)
            throw new ConfigurationParsingException("Cannot resolve reference: " + ref, null, ctx.key(key));
        return resolved;
    }
}
