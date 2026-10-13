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
import com.predic8.membrane.core.util.*;

import java.time.*;
import java.time.format.*;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

/**
 * @description Publishes every API deployed on this gateway as an APIs.json 0.18 document, a
 * collection a crawler or registry can index. The catalog is collected from the running
 * configuration on each request and therefore never goes stale.
 * <p>
 * APIs.json treats the <code>aid</code> of the collection and of each API as a stable identity.
 * Without <code>baseUrl</code> those identifiers are built from the host each client used, so two
 * crawlers that reach the gateway under different names index two different collections. Set
 * <code>baseUrl</code> when the document is meant for crawlers or registries.
 * </p>
 * <p>
 * An API described by an OpenAPI document is listed with the title, description and version from
 * that document, plus links to the document itself and to its Swagger UI. A SOAP proxy is listed
 * with a link to the WSDL the gateway publishes. Any other proxy is listed with its own name and
 * URL. Internal proxies and proxies whose initialisation failed are left out.
 * </p>
 * <p>
 * Answers every request that reaches it and ends the flow, so give it an api of its own. RFC 9727
 * reserves <code>/.well-known/api-catalog</code> for its own format, so that path is refused here;
 * use <code>apiCatalog</code> there.
 * </p>
 * <pre>
 * apisJson:
 *   [ baseUrl: &lt;url&gt; ]
 *   [ collectionId: &lt;id&gt; ]
 *   [ name: &lt;name&gt; ]
 *   [ description: &lt;text&gt; ]
 *   [ created: &lt;yyyy-mm-dd&gt; ]
 *   [ modified: &lt;yyyy-mm-dd&gt; ]
 * </pre>
 * @topic 5. OpenAPI
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   path:
 *     uri: /apis.json
 *   flow:
 *     - apisJson:
 *         baseUrl: https://api.example.com
 *         collectionId: gateway
 *         name: Example APIs
 * </code></pre>
 */
@MCElement(name = "apisJson")
public class ApisJsonInterceptor extends AbstractApiDiscoveryInterceptor {

    private String collectionId = "apis";
    private String collectionName = "APIs";
    private String description = "APIs served by this gateway";
    private LocalDate created = LocalDate.now();
    private LocalDate modified = LocalDate.now();

    @Override
    protected CatalogRenderer createRenderer() {
        return new ApisJsonRenderer(collection(), created, modified);
    }

    public String getCollectionId() {
        return collectionId;
    }

    /**
     * @description Identifier of the collection itself, forming the second half of its
     * <code>&lt;domain&gt;:&lt;collectionId&gt;</code> identifier.
     * @default apis
     * @example gateway
     */
    @MCAttribute
    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
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

    public LocalDate getCreated() {
        return created;
    }

    /**
     * @description Day the collection was created, as an ISO date. APIs.json requires the member,
     * so the document always carries it.
     * @default the day Membrane started
     * @example 2026-01-15
     */
    @MCAttribute
    public void setCreated(String created) {
        this.created = parseDate("created", created);
    }

    public LocalDate getModified() {
        return modified;
    }

    /**
     * @description Day the collection last changed, as an ISO date. APIs.json requires the member,
     * so the document always carries it.
     * @default the day Membrane started
     * @example 2026-01-15
     */
    @MCAttribute
    public void setModified(String modified) {
        this.modified = parseDate("modified", modified);
    }

    private LocalDate parseDate(String attribute, String value) {
        try {
            return LocalDate.parse(value, ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new ConfigurationException(
                    "Cannot parse %s/@%s \"%s\". Expected an ISO date such as 2026-01-15."
                            .formatted(elementName(), attribute, value), e);
        }
    }

    private CollectionInfo collection() {
        return new CollectionInfo(collectionId, collectionName, description);
    }

    @Override
    public String getDisplayName() {
        return "APIs.json";
    }

    @Override
    public String getShortDescription() {
        return "Publishes all deployed APIs as an APIs.json document.";
    }
}
