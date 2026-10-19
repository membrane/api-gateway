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
import com.predic8.membrane.core.proxies.*;

import java.time.*;
import java.util.*;

/**
 * @description Publishes every API deployed on this gateway as an APIs.json 0.24 document, a
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
 * URL. Internal proxies and proxies whose initialisation failed are left out. The tags of the
 * listed APIs become the tags of the collection.
 * </p>
 * <p>
 * The <code>info</code> block of an api adds what the gateway cannot derive: its contact, image,
 * dates, further properties such as pricing or changelog, overlays, prompts, rules and workflows.
 * </p>
 * <p>
 * Answers every request that reaches it and ends the flow, so give it an api of its own. RFC 9727
 * reserves <code>/.well-known/api-catalog</code> for its own format, so that path is refused here;
 * use <code>apiCatalog</code> there.
 * </p>
 * <pre>
 * apisJson:
 *   [ baseUrl: &lt;url&gt; ]
 *   [ tags:
 *       - include | exclude: &lt;tag&gt; ]
 *   [ collectionId: &lt;id&gt; ]
 *   [ name: &lt;name&gt; ]
 *   [ description: &lt;text&gt; ]
 *   [ image: &lt;url&gt; ]
 *   [ type: index | collection | blueprint ]
 *   [ kind: &lt;kind&gt; ]
 *   [ position: &lt;position&gt; ]
 *   [ access: &lt;access&gt; ]
 *   [ created: &lt;yyyy-mm-dd&gt; ]
 *   [ modified: &lt;yyyy-mm-dd&gt; ]
 *   [ maintainers:
 *       - [ name: &lt;name&gt; ]
 *         [ email: &lt;email&gt; ]
 *         [ url: &lt;url&gt; ]
 *         [ organization | address | phone | twitter | github | photo | vCard: &lt;value&gt; ] ]
 *   [ common | prompts | rules | workflows:
 *       - type: &lt;type&gt;
 *         url: &lt;url&gt; | data: &lt;text&gt;
 *         [ name: &lt;name&gt; ]
 *         [ description: &lt;text&gt; ]
 *         [ mediaType: &lt;media type&gt; ]
 *         [ tags: [ &lt;tag&gt;, ... ] ] ]
 *   [ overlays:
 *       - type: &lt;type&gt;
 *         url: &lt;url&gt; ]
 *   [ include | networks:
 *       - name: &lt;name&gt;
 *         url: &lt;url&gt; ]
 * </pre>
 * @topic 5. OpenAPI
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   path:
 *     uri: /.well-known/apis.json
 *   flow:
 *     - apisJson:
 *         baseUrl: https://api.example.com
 *         collectionId: gateway
 *         name: Example APIs
 *         type: collection
 *         maintainers:
 *           - name: API Team
 *             url: https://example.com/api-team
 *         common:
 *           - type: Signup
 *             url: https://example.com/signup
 * </code></pre>
 */
@MCElement(name = "apisJson")
public class ApisJsonInterceptor extends AbstractCollectionDiscoveryInterceptor {

    public enum CollectionType {
        INDEX("Index"), COLLECTION("Collection"), BLUEPRINT("Blueprint");

        private final String value;

        CollectionType(String value) {
            this.value = value;
        }
    }

    private String collectionId = "apis";
    private CollectionType type;
    private String kind;
    private String image;
    private String position;
    private String access;
    private LocalDate created = LocalDate.now();
    private LocalDate modified = LocalDate.now();
    private List<ProxyContact> maintainers = List.of();
    private List<CatalogResource.Common> common = List.of();
    private List<CatalogResource.Prompt> prompts = List.of();
    private List<CatalogResource.Rule> rules = List.of();
    private List<CatalogResource.Workflow> workflows = List.of();
    private List<CatalogOverlay> overlays = List.of();
    private List<CatalogReference.Include> include = List.of();
    private List<CatalogReference.Network> networks = List.of();

    @Override
    public void init() {
        common.forEach(CatalogResource::validate);
        prompts.forEach(CatalogResource::validate);
        rules.forEach(CatalogResource::validate);
        workflows.forEach(CatalogResource::validate);
        include.forEach(CatalogReference::validate);
        super.init();
    }

    @Override
    protected CatalogRenderer createRenderer() {
        return new ApisJsonRenderer(collection(), ApisJsonCollection.builder(collectionId, created, modified)
                .type(type == null ? null : type.value)
                .kind(kind)
                .image(image)
                .position(position)
                .access(access)
                .maintainers(ConfiguredMetadata.contacts(maintainers))
                .common(ConfiguredMetadata.resources(common))
                .prompts(ConfiguredMetadata.resources(prompts))
                .rules(ConfiguredMetadata.resources(rules))
                .workflows(ConfiguredMetadata.resources(workflows))
                .overlays(ConfiguredMetadata.overlays(overlays))
                .include(ConfiguredMetadata.references(include))
                .networks(ConfiguredMetadata.references(networks))
                .build());
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

    public CollectionType getType() {
        return type;
    }

    /**
     * @description Type of the collection: <code>index</code> for the description of a single API,
     * <code>collection</code> for several APIs, <code>blueprint</code> for the design of a new API.
     * Without it, the document has no type.
     * @example collection
     */
    @MCAttribute
    public void setType(CollectionType type) {
        this.type = type;
    }

    public String getKind() {
        return kind;
    }

    /**
     * @description Kind of entity the collection describes, such as <code>company</code>,
     * <code>government</code>, <code>opensource</code>, <code>standard</code>, <code>contract</code>
     * or <code>topic</code>. Without it, the document has no kind.
     * @example company
     */
    @MCAttribute
    public void setKind(String kind) {
        this.kind = kind;
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
        this.created = ProxyInfo.parseDate(elementName(), "created", created);
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
        this.modified = ProxyInfo.parseDate(elementName(), "modified", modified);
    }

    public List<ProxyContact> getMaintainers() {
        return maintainers;
    }

    /**
     * @description People or teams maintaining the collection, each with a name, an email address
     * or a web page.
     */
    @MCChildElement(order = 1)
    public void setMaintainers(List<ProxyContact> maintainers) {
        this.maintainers = maintainers;
    }

    public String getImage() {
        return image;
    }

    /**
     * @description URL of an image that represents the collection.
     * @example https://example.com/logo.png
     */
    @MCAttribute
    public void setImage(String image) {
        this.image = image;
    }

    public String getPosition() {
        return position;
    }

    /**
     * @description Position the maintainer of the collection holds towards its APIs, such as
     * <code>Producer</code> or <code>Consumer</code>.
     * @example Producer
     */
    @MCAttribute
    public void setPosition(String position) {
        this.position = position;
    }

    public String getAccess() {
        return access;
    }

    /**
     * @description Access available to the APIs of the collection, such as <code>Public</code>,
     * <code>Partner</code> or <code>Private</code>.
     * @example Public
     */
    @MCAttribute
    public void setAccess(String access) {
        this.access = access;
    }

    public List<CatalogResource.Common> getCommon() {
        return common;
    }

    /**
     * @description Properties shared by every API of the collection, such as the signup page, the
     * terms of service or the support forum.
     */
    @MCChildElement(order = 2)
    public void setCommon(List<CatalogResource.Common> common) {
        this.common = List.copyOf(common);
    }

    public List<CatalogResource.Prompt> getPrompts() {
        return prompts;
    }

    /**
     * @description AI prompts for every API of the collection.
     */
    @MCChildElement(order = 3)
    public void setPrompts(List<CatalogResource.Prompt> prompts) {
        this.prompts = List.copyOf(prompts);
    }

    public List<CatalogResource.Rule> getRules() {
        return rules;
    }

    /**
     * @description Governance rulesets every API of the collection follows.
     */
    @MCChildElement(order = 4)
    public void setRules(List<CatalogResource.Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<CatalogResource.Workflow> getWorkflows() {
        return workflows;
    }

    /**
     * @description Workflows, such as Arazzo documents, across the APIs of the collection.
     */
    @MCChildElement(order = 5)
    public void setWorkflows(List<CatalogResource.Workflow> workflows) {
        this.workflows = List.copyOf(workflows);
    }

    public List<CatalogOverlay> getOverlays() {
        return overlays;
    }

    /**
     * @description Overlays that augment the collection.
     */
    @MCChildElement(order = 6)
    public void setOverlays(List<CatalogOverlay> overlays) {
        this.overlays = List.copyOf(overlays);
    }

    public List<CatalogReference.Include> getInclude() {
        return include;
    }

    /**
     * @description Other APIs.json documents whose APIs belong to this collection, for example the
     * catalogs of further gateways.
     */
    @MCChildElement(order = 7)
    public void setInclude(List<CatalogReference.Include> include) {
        this.include = List.copyOf(include);
    }

    public List<CatalogReference.Network> getNetworks() {
        return networks;
    }

    /**
     * @description Related nodes of an API network that clients can search.
     */
    @MCChildElement(order = 8)
    public void setNetworks(List<CatalogReference.Network> networks) {
        this.networks = List.copyOf(networks);
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
