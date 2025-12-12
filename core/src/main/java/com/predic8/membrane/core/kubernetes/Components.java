package com.predic8.membrane.core.kubernetes;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

import java.util.List;

@MCElement(name = "components"
        , noEnvelope = true
        , topLevel = true
)
public class Components {

    List<Object> components;

    @MCChildElement
    public void setComponents(List<Object> components) {
        this.components = components;
    }

    public List<Object> getComponents() {
        return components;
    }

}
