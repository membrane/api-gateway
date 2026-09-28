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
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import static com.predic8.membrane.core.http.MimeType.*;

/**
 * Renders a {@link Catalog} as an RFC 9727 api-catalog document.
 * <p>
 * The document is a linkset (RFC 9264): one object whose sole member is <code>linkset</code>, an
 * array of link context objects. Every link relation inside a context object maps to an array of
 * link targets, even when there is only one. The first context object is anchored at the catalog
 * itself and bookmarks each API with <code>item</code>; one further context object per API is
 * anchored at that API and points at its OpenAPI document with <code>service-desc</code> and at
 * its Swagger UI with <code>service-doc</code>.
 * <p>
 * Publish this at <code>/.well-known/api-catalog</code>, the well-known URI RFC 9727 defines.
 *
 * @see <a href="https://www.rfc-editor.org/info/rfc9727">RFC 9727</a>
 * @see <a href="https://www.rfc-editor.org/info/rfc9264">RFC 9264</a>
 */
public class ApiCatalogRenderer implements CatalogRenderer {

    static final String PROFILE = "https://www.rfc-editor.org/info/rfc9727";

    private static final ObjectMapper om = new ObjectMapper();

    @Override
    public String contentType() {
        return APPLICATION_LINKSET_JSON + ";profile=\"" + PROFILE + "\"";
    }

    @Override
    public byte[] render(Catalog catalog) throws JsonProcessingException {
        ObjectNode document = om.createObjectNode();
        ArrayNode linkset = document.putArray("linkset");
        renderBookmarks(catalog, linkset);
        catalog.apis().forEach(entry -> renderApi(entry, linkset));
        return om.writeValueAsBytes(document);
    }

    /**
     * The catalog itself as link context, listing every API with the <code>item</code> relation.
     */
    private void renderBookmarks(Catalog catalog, ArrayNode linkset) {
        ObjectNode context = om.createObjectNode();
        if (catalog.url() != null)
            context.put("anchor", catalog.url());
        ArrayNode items = context.putArray("item");
        catalog.apis().forEach(entry -> {
            ObjectNode item = om.createObjectNode();
            item.put("href", entry.endpoint().url());
            item.put("title", entry.name());
            items.add(item);
        });
        linkset.add(context);
    }

    /**
     * One API as link context. Skipped when nothing is known beyond the API's own URL, which is
     * already covered by the bookmark above.
     */
    private void renderApi(CatalogEntry entry, ArrayNode linkset) {
        ObjectNode context = om.createObjectNode();
        context.put("anchor", entry.endpoint().url());
        if (entry.links().specUrl() != null)
            context.putArray("service-desc").add(link(entry.links().specUrl(), APPLICATION_X_YAML));
        if (entry.links().humanUrl() != null)
            context.putArray("service-doc").add(link(entry.links().humanUrl(), TEXT_HTML));
        if (context.size() > 1)
            linkset.add(context);
    }

    private ObjectNode link(String href, String type) {
        ObjectNode link = om.createObjectNode();
        link.put("href", href);
        link.put("type", type);
        return link;
    }
}
