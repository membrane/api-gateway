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
