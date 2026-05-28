package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.util.ConfigurationException;

@MCElement(name = "batch", component = false)
public class BatchRule {

    private boolean enabled = true;

    private Integer maxSize = 100;

    @MCAttribute
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @MCAttribute
    public void setMaxSize(Integer maxSize) {
        if (maxSize == null || maxSize < 1) {
            throw new ConfigurationException("batch maxSize must be greater than 0");
        }
        this.maxSize = maxSize;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
