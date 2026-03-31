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

package com.predic8.membrane.annot.yaml.parsing.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.annot.Grammar;
import com.predic8.membrane.annot.yaml.JsonLocationMap;
import com.predic8.membrane.annot.yaml.ParsingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class YamlDocumentReader {

    private static final Logger log = LoggerFactory.getLogger(YamlDocumentReader.class);
    private final Grammar grammar;

    public YamlDocumentReader(Grammar grammar) {
        this.grammar = grammar;
    }

    public List<ResolvedDocument> readDocuments(SourceMetadata sourceMetadata, String yaml) throws IOException {
        List<ResolvedDocument> documents = new ArrayList<>();
        JsonLocationMap jsonLocationMap = new JsonLocationMap();
        for (JsonNode jsonNode : jsonLocationMap.parseWithLocations(yaml)) {
            if (jsonNode == null || jsonNode.isNull() || jsonNode.isEmpty()) {
                log.debug("Skipping empty document. Maybe there are two --- separators but no configuration in between.");
                continue;
            }
            documents.add(new ResolvedDocument(
                    jsonNode,
                    sourceMetadata,
                    new ParsingContext<>("", null, grammar, jsonNode, "$", null)
            ));
        }
        return documents;
    }


    public record ResolvedDocument(JsonNode node, SourceMetadata sourceMetadata, ParsingContext<?> parsingContext) {

        public boolean isIncludeDocument() {
            return node.isObject() && node.size() == 1 && node.has("include");
        }
    }
}
