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
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.util.*;

/**
 * A named link to another catalog document or catalog node.
 */
public abstract class CatalogReference {

    private String name;
    private String url;

    public void validate() {
    }

    public String getName() {
        return name;
    }

    /**
     * @description Name of the referenced document or node.
     * @example Partner APIs
     */
    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    /**
     * @description URL of the referenced document or node.
     * @example https://partner.example.com/.well-known/apis.json
     */
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * @description Another APIs.json document whose APIs belong to this collection, for example the
     * catalog of a second gateway. APIs.json requires both name and url.
     */
    @MCElement(name = "includedCollection", component = false, id = "apisJson-include")
    public static class Include extends CatalogReference {

        @Override
        public void validate() {
            if (getName() == null || getUrl() == null)
                throw new ConfigurationException("An included APIs.json collection needs a name and a url.");
        }
    }

    /**
     * @description A related node of an API network that clients can search.
     */
    @MCElement(name = "network", component = false, id = "apisJson-network")
    public static class Network extends CatalogReference {
    }
}
