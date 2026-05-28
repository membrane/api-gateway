package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.util.ConfigurationException;

/**
 * @description Controls whether JSON-RPC batch requests are allowed and how many request objects a batch may contain.
 */
@MCElement(name = "batch", component = false)
public class BatchRule {

    private boolean enabled = true;

    private Integer maxSize = 100;

    /**
     * @description Enables or disables JSON-RPC batch requests.
     * @default true
     */
    @MCAttribute
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @description The maximum number of request objects allowed in one JSON-RPC batch.
     * @default 100
     * @example 50
     */
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
