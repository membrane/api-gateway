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

import java.util.*;

/**
 * The APIs deployed on this gateway, as one request sees them. A {@link CatalogCollector} builds
 * one per request, a {@link CatalogRenderer} turns it into a concrete document. Everything every
 * format needs is collected once, so adding a format means adding a renderer rather than another
 * traversal of the router's proxies. Metadata only some formats carry, such as the name of the
 * collection, lives in {@link CollectionInfo} instead.
 *
 * @param domain domain identifiers are built from: the host of the configured base URL, else the
 *               host the client used
 * @param url    URL the catalog document itself is published under
 * @param apis   the APIs of the collection, never null
 */
public record Catalog(String domain, String url, List<CatalogEntry> apis) {

    public Catalog {
        apis = List.copyOf(apis);
    }

}
