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
package com.predic8.membrane.annot.yaml;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

public class BeanDefinition {

    public static final String PROTOTYPE = "prototype";

    private final String name;
    private final String namespace;
    private final String uid;
    private final JsonNode node;
    private final WatchAction action;
    private final String kind;
    private Object bean;

    /**
     * Only called from K8S.
     */
    private BeanDefinition(WatchAction action, JsonNode node) {
        this.action = action;
        this.node = node;
        JsonNode metadata = node.get("metadata"); // TODO What if metadata is null?
        var kind2 = node.get("kind").asText();
        if (kind2 == null)
            kind2 = "api";
        kind = kind2;
        name = metadata.get("name").asText();
        if (name == null)
            throw new IllegalArgumentException("name is null");
        namespace = metadata.get("namespace").asText();
        uid = metadata.get("uid").asText();
    }

    public static BeanDefinition create4Kubernetes(WatchAction action, JsonNode node) {
        return new BeanDefinition(action, node);
    }

    public BeanDefinition(String kind, String name, String namespace, String uid, JsonNode node) {
        this.kind = kind;
        this.name = name;
        this.namespace = namespace;
        this.uid = uid;
        this.node = node;
        this.action = WatchAction.ADDED;
    }

    public JsonNode getNode() {
        return node;
    }

    public WatchAction getAction() {
        return action;
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

    public Object getBean() {
        return bean;
    }

    // TODO: Rest is immutable - can we make this also?
    public void setBean(Object bean) {
        this.bean = bean;
    }

    public String getScope() {
        JsonNode meta = node.get("metadata");
        if (meta == null)
            return null;
        JsonNode annotations = meta.get("annotations");
        if (annotations == null)
            return null;
        return annotations.get("membrane-soa.org/scope").asText(); // TODO migrate to membrane-api.io
    }

    public boolean isPrototype() {
        return PROTOTYPE.equals(getScope());
    }
}
