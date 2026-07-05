/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.util.*;

/**
 * @description A named API that is not exposed on any port and can only be invoked by other APIs
 * within the gateway using the <code>internal://name</code> URL scheme.
 * Use it to factor out shared flow logic that multiple APIs call.
 * @topic 1. Proxies and Flow
 * @yaml
 * <pre><code>
 * internal:
 *   name: shared-auth
 *   flow:
 *     - setHeader:
 *         name: X-Api-Key
 *         value: downstream-secret
 *   target:
 *     url: https://api.example.com
 *
 * ---
 * api:
 *   port: 2000
 *   target:
 *     url: internal://shared-auth
 *
 * ---
 * api:
 *   port: 3000
 *   target:
 *     url: internal://shared-auth
 * </code></pre>
 */
@MCElement(name="internal", topLevel = true, component = false)
public class InternalProxy extends AbstractServiceProxy implements NotPortOpeningProxy {

    public InternalProxy() {
        key = new InternalProxyKey();
    }

    @Override
    public void init() {
        ((InternalProxyKey)key).setServiceName(getName());

        if(key.getPort() != 0) {
            throw new ConfigurationException("""
                    Internal proxy with name %s was configured to open port %s. A internal proxy is never exposed to the outside. Configuration
                    of the port must be removed!
                    """.formatted(name,key.getPort()));
        }
    }
}