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
package com.predic8.membrane.core.interceptor.administration;

import com.predic8.membrane.annot.*;

/**
 * @description Another Membrane instance whose admin API this admin console includes, for example a redundant
 * instance with its own exchange store. The peer runs an <code>adminConsole</code> as well.
 * @yaml <pre><code>
 * api:
 *   port: 9000
 *   flow:
 *     - adminConsole:
 *         peers:
 *           - url: http://node-b:9000
 *             apiKey: ${ADMIN_API_KEY}
 * </code></pre>
 */
@MCElement(name = "peer", component = false)
public class AdminConsolePeer {

    private String url;
    private String apiKey;
    private String name;

    public String getUrl() {
        return url;
    }

    /**
     * @description Base URL of the API that contains the peer's <code>adminConsole</code>.
     * @example http://node-b:9000
     */
    @Required
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * @description API key sent in the <code>X-Api-Key</code> header. Must match the <code>apiKey</code> of the peer's
     * <code>adminConsole</code>.
     */
    @MCAttribute
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getName() {
        return name;
    }

    /**
     * @description Instance name of the peer. If not set, it is read from the peer.
     * @default the <code>instanceName</code> of the peer
     */
    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }
}
