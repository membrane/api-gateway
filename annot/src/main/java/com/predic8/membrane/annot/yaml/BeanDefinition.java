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

import java.util.*;

public class BeanDefinition {

    private final String name;
    private final String namespace;
    private final String uid;
    private final Map<String,Object> m;
    private final WatchAction action;
    private final String kind;
    private Object bean;

    public BeanDefinition(WatchAction action, Map<String,Object> m) {
        this.action = action;
        this.m = m;
        Map<String,Object> metadata = (Map<String,Object>) m.get("metadata");
        var kind2 = (String) m.get("kind");
        if (kind2 == null)
            kind2 = "api";
        kind = kind2;
        name = (String) metadata.get("name");
        if (name == null)
            throw new IllegalArgumentException("name is null");
        namespace = (String) metadata.get("namespace");
        uid = (String) metadata.get("uid");
    }

    public BeanDefinition(String kind, String name, String namespace, String uid, Map<String,Object> m) {
        this.kind = kind;
        this.name = name;
        this.namespace = namespace;
        this.uid = uid;
        this.m = m;
        this.action = WatchAction.ADDED;
    }

    public Map<String,Object> getMap() {
        return m;
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

    public void setBean(Object bean) {
        this.bean = bean;
    }

    public String getScope() {
        Map<String,Object> meta = (Map<String,Object>) getMap().get("metadata");
        if (meta == null)
            return null;
        Map<String,Object> annotations = (Map<String,Object>) meta.get("annotations");
        if (annotations == null)
            return null;
        return (String) annotations.get("membrane-soa.org/scope"); // TODO migrate to membrane-api.io
    }
}
