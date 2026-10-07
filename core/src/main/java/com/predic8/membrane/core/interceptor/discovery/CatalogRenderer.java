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

import com.fasterxml.jackson.core.*;

/**
 * Renders a {@link Catalog} into one concrete discovery format. Implementations are stateless and
 * are shared by every request thread of an {@link ApiDiscoveryInterceptor}.
 */
public interface CatalogRenderer {

    /**
     * Value for the <code>Content-Type</code> header of the rendered document.
     */
    String contentType();

    byte[] render(Catalog catalog) throws JsonProcessingException;

    /**
     * Whether {@link #render} reads {@link CatalogEntry#operations()}. Collecting operations means
     * walking every path of every OpenAPI document, by far the most expensive part of building a
     * catalog, so a renderer that ignores them lets {@link CatalogCollector} skip that work
     * entirely.
     */
    default boolean rendersOperations() {
        return false;
    }
}
