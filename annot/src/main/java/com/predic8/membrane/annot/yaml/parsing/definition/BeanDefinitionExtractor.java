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
import com.predic8.membrane.annot.beanregistry.BeanDefinition;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.annot.yaml.ParsingContext;
import com.predic8.membrane.annot.yaml.parsing.ParseSession;
import com.predic8.membrane.annot.yaml.parsing.source.ResolvedDocument;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.annot.yaml.NodeValidationUtils.ensureSingleKey;
import static java.util.UUID.randomUUID;

public final class BeanDefinitionExtractor {

    private final ComponentDefinitionExtractor componentDefinitionExtractor;

    public BeanDefinitionExtractor(ComponentDefinitionExtractor componentDefinitionExtractor) {
        this.componentDefinitionExtractor = componentDefinitionExtractor;
    }

    public List<BeanDefinition> extract(ParseSession session, List<ResolvedDocument> documents) {
        List<BeanDefinition> defs = new ArrayList<>();
        for (ResolvedDocument document : documents) {
            try {
                JsonNode jsonNode = document.node();
                ParsingContext<?> pc = document.parsingContext();
                String beanType = getBeanType(pc, jsonNode);

                if ("components".equals(beanType)) {
                    defs.addAll(componentDefinitionExtractor.extract(
                            session,
                            pc.addPath(".components"),
                            jsonNode.get("components"),
                            document.sourceMetadata()));
                }

                defs.add(new BeanDefinition(
                        beanType,
                        session.nextBeanName(),
                        "default",
                        randomUUID().toString(),
                        jsonNode,
                        document.sourceMetadata()));
            } catch (ConfigurationParsingException e) {
                if (e.getSourceFile() == null && document.sourceContext().sourceFile() != null) {
                    e.setSourceFile(document.sourceContext().sourceFile());
                }
                throw e;
            }
        }
        return defs;
    }

    private static String getBeanType(ParsingContext<?> ctx, JsonNode jsonNode) {
        ensureSingleKey(ctx, jsonNode);
        return jsonNode.fieldNames().next();
    }
}
