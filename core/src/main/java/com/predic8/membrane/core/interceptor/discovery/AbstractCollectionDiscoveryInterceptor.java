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

import com.predic8.membrane.annot.*;

/**
 * Base of the formats that describe the collection as a whole with a name and a description,
 * such as APIs.json. An RFC 9727 linkset has neither.
 */
public abstract class AbstractCollectionDiscoveryInterceptor extends AbstractApiDiscoveryInterceptor {

    private String collectionName = "APIs";
    private String description = "APIs served by this gateway";

    protected CollectionInfo collection() {
        return new CollectionInfo(collectionName, description);
    }

    public String getCollectionName() {
        return collectionName;
    }

    /**
     * @description Human readable name of the collection.
     * @default APIs
     * @example Membrane Gateway APIs
     */
    @MCAttribute(attributeName = "name")
    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * @description Human readable description of the collection.
     * @default APIs served by this gateway
     * @example Every API of the payments platform
     */
    @MCAttribute
    public void setDescription(String description) {
        this.description = description;
    }
}
