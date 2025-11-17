package com.predic8.membrane.core.config.xml;

import com.predic8.membrane.annot.*;

@MCElement(name="xmlConfig",topLevel = true)
public class XmlConfig {

    private Namespaces namespaces;

    @MCChildElement
    public void setNamespaces(Namespaces namespaces) {
        this.namespaces = namespaces;
    }

    public Namespaces getNamespaces() {
        return namespaces;
    }
}