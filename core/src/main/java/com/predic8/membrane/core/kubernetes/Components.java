package com.predic8.membrane.core.kubernetes;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;

import java.util.List;
import java.util.Map;

@MCElement(name = "components", topLevel = true)
public class Components {

    Map<String, Object> components;

    public Map<String, Object> getComponents() {
        return components;
    }

    @MCOtherAttributes
    public void setComponents(Map<String, Object> components) {
        this.components.putAll(components);
    }

}
