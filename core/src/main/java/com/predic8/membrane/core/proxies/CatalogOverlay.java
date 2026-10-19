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

/**
 * @description An overlay that augments the catalog entry, such as a rating or a search index.
 */
@MCElement(name = "overlay", component = false, id = "catalog-overlay")
public class CatalogOverlay {

    private String type;
    private String url;

    public String getType() {
        return type;
    }

    /**
     * @description Type of the overlay.
     * @example APIs.io Search
     */
    @MCAttribute
    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    /**
     * @description URL of the overlay.
     * @example https://example.com/apis-io-search
     */
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }
}
