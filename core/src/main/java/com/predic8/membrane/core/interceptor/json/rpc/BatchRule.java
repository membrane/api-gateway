package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "batch", component = false)
public class BatchRule {

    private boolean enabled = true; // TODO

    private Integer maxSize = 100; // TODO

    @MCAttribute
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @MCAttribute
    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
