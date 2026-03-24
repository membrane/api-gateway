/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot.beanregistry;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.yaml.*;

import java.nio.file.Path;

import static com.predic8.membrane.annot.beanregistry.BeanDefinition.SourceMetadata.empty;

/**
 * Immutable.
 */
public class BeanDefinition {

    public static final String PROTOTYPE = "prototype";

    private final String name;
    private final String namespace;
    private final String uid;
    private final JsonNode node;
    private final String kind;
    private final SourceMetadata sourceMetadata;

    public record SourceMetadata(Path basePath, Path sourceFile, Path rootSourceFile) {
        public static SourceMetadata empty() {
            return new SourceMetadata(null, null, null);
        }
    }

    /**
     * Only called from K8S.
     */
    private BeanDefinition(JsonNode node) {
        this.node = node;
        JsonNode metadata = node.get("metadata");
        var kind2 = node.get("kind").asText();
        if (kind2 == null)
            kind2 = "api";
        kind = kind2;
        name = metadata.get("name").asText();
        if (name == null)
            throw new IllegalArgumentException("name is null");
        namespace = metadata.get("namespace").asText();
        uid = metadata.get("uid").asText();
        sourceMetadata = empty();
    }

    public static BeanDefinitionChanged create4Kubernetes(WatchAction action, JsonNode node) {
        return new BeanDefinitionChanged(action, new BeanDefinition(node));
    }

    public BeanDefinition(String kind, String name, String namespace, String uid, JsonNode node) {
        this(kind, name, namespace, uid, node, empty());
    }

    public BeanDefinition(String kind, String name, String namespace, String uid, JsonNode node, SourceMetadata sourceMetadata) {
        this.kind = kind;
        this.name = name;
        this.namespace = namespace;
        this.uid = uid;
        this.node = node;
        this.sourceMetadata = sourceMetadata == null ? empty() : sourceMetadata;
    }

    public JsonNode getNode() {
        return node;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public String getUid() {
        return uid;
    }

    public String getKind() {
        return kind;
    }

    public SourceMetadata getSourceMetadata() {
        return sourceMetadata;
    }

    public String getScope() {
        if (node == null)
            return null;
        JsonNode meta = node.get("metadata");
        if (meta == null)
            return null;
        JsonNode annotations = meta.get("annotations");
        if (annotations == null)
            return null;
        JsonNode scope = annotations.get("membrane-api.io/scope");
        return scope == null ? null : scope.asText();
    }

    public boolean isComponent() {
        return name != null && name.startsWith("#/components/");
    }

    public boolean isBean() {
        return "bean".equals(kind);
    }

    public boolean isPrototype() {
        return PROTOTYPE.equals(getScope());
    }

    @Override
    public String toString() {
        return "BeanDefinition{" +
               "name='" + name + '\'' +
                ", namespace='" + namespace + '\'' +
                ", uid='" + uid + '\'' +
                ", node=" + node +
                ", kind='" + kind + '\'' +
                ", sourceMetadata=" + sourceMetadata +
                '}';
    }
}
