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

package com.predic8.membrane.annot.yaml.parsing.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.annot.beanregistry.BeanDefinition;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.annot.yaml.ParsingContext;
import com.predic8.membrane.annot.yaml.parsing.ParseSession;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.predic8.membrane.annot.yaml.NodeValidationUtils.ensureSingleKey;
import static java.util.List.of;
import static java.util.UUID.randomUUID;

public final class ComponentDefinitionExtractor {

    public List<BeanDefinition> extract(ParseSession session, ParsingContext<?> pc, JsonNode componentsNode, BeanDefinition.SourceMetadata sourceMetadata) {
        if (componentsNode == null || componentsNode.isNull())
            return of();

        if (!componentsNode.isObject())
            throw new ConfigurationParsingException("Expected object for 'components'.");

        List<BeanDefinition> res = new ArrayList<>();

        Iterator<String> ids = componentsNode.fieldNames();
        while (ids.hasNext()) {
            String id = ids.next();
            JsonNode def = componentsNode.get(id);
            String componentRef = "#/components/" + id;

            if (!session.componentIds().add(componentRef))
                throw new ConfigurationParsingException("Duplicate component id '%s'. Component ids must be unique across all included files.".formatted(componentRef), null, pc.addPath("." + id));

            ensureSingleKey(pc.addPath("." + id), def);
            String componentKind = def.fieldNames().next();

            ObjectNode wrapped = JsonNodeFactory.instance.objectNode();
            wrapped.set(componentKind, def.get(componentKind));

            res.add(new BeanDefinition(
                    componentKind,
                    componentRef,
                    "default",
                    randomUUID().toString(),
                    wrapped,
                    sourceMetadata
            ));
        }
        return res;
    }
}
