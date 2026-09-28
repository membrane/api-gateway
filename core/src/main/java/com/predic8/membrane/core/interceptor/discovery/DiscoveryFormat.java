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
package com.predic8.membrane.core.interceptor.discovery;

/**
 * Document format the <code>apiDiscovery</code> plugin publishes. Constant names are matched
 * case-insensitively in the configuration, so <code>apisjson</code> and <code>apisJson</code>
 * both select {@link #APISJSON}.
 */
public enum DiscoveryFormat {

    /**
     * APIs.json 0.18, see <a href="https://apisjson.org/">apisjson.org</a>.
     */
    APISJSON,

    /**
     * RFC 9727 api-catalog, an <code>application/linkset+json</code> document.
     */
    APICATALOG,

    /**
     * Membrane's own format. Carries everything the gateway knows about an API, including the
     * methods and paths of its operations.
     */
    MEMBRANE
}
