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

import java.time.*;
import java.time.format.*;
import java.util.*;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

/**
 * @description Metadata about the API that API catalogs publish.
 */
@MCElement(name = "info", component = false)
public class ProxyInfo {

    private ProxyContact contact;
    private String image;
    private LocalDate created;
    private LocalDate modified;
    private List<CatalogResource.Property> properties = List.of();
    private List<CatalogOverlay> overlays = List.of();
    private List<CatalogResource.Prompt> prompts = List.of();
    private List<CatalogResource.Rule> rules = List.of();
    private List<CatalogResource.Workflow> workflows = List.of();

    public void validate() {
        properties.forEach(CatalogResource::validate);
        prompts.forEach(CatalogResource::validate);
        rules.forEach(CatalogResource::validate);
        workflows.forEach(CatalogResource::validate);
    }

    /**
     * Parses an ISO date such as <code>2026-01-15</code>, naming the offending attribute otherwise.
     */
    public static LocalDate parseDate(String element, String attribute, String value) {
        try {
            return LocalDate.parse(value, ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new ConfigurationException(
                    "Cannot parse %s/@%s \"%s\". Expected an ISO date such as 2026-01-15."
                            .formatted(element, attribute, value), e);
        }
    }

    public ProxyContact getContact() {
        return contact;
    }

    /**
     * @description Whom to contact about the API.
     */
    @MCChildElement
    public void setContact(ProxyContact contact) {
        this.contact = contact;
    }

    public String getImage() {
        return image;
    }

    /**
     * @description URL of an image that represents the API, such as an icon in a search result.
     * @example https://example.com/orders.png
     */
    @MCAttribute
    public void setImage(String image) {
        this.image = image;
    }

    public LocalDate getCreated() {
        return created;
    }

    /**
     * @description Day the API was created, as an ISO date.
     * @example 2026-01-15
     */
    @MCAttribute
    public void setCreated(String created) {
        this.created = parseDate("info", "created", created);
    }

    public LocalDate getModified() {
        return modified;
    }

    /**
     * @description Day the API last changed, as an ISO date.
     * @example 2026-03-01
     */
    @MCAttribute
    public void setModified(String modified) {
        this.modified = parseDate("info", "modified", modified);
    }

    public List<CatalogResource.Property> getProperties() {
        return properties;
    }

    /**
     * @description Further properties of the API, such as its pricing page, changelog or status
     * page. They are listed after the links Membrane derives itself, like the OpenAPI document.
     */
    @MCChildElement(order = 1)
    public void setProperties(List<CatalogResource.Property> properties) {
        this.properties = List.copyOf(properties);
    }

    public List<CatalogOverlay> getOverlays() {
        return overlays;
    }

    /**
     * @description Overlays that augment the catalog entry of the API.
     */
    @MCChildElement(order = 2)
    public void setOverlays(List<CatalogOverlay> overlays) {
        this.overlays = List.copyOf(overlays);
    }

    public List<CatalogResource.Prompt> getPrompts() {
        return prompts;
    }

    /**
     * @description AI prompts for the API.
     */
    @MCChildElement(order = 3)
    public void setPrompts(List<CatalogResource.Prompt> prompts) {
        this.prompts = List.copyOf(prompts);
    }

    public List<CatalogResource.Rule> getRules() {
        return rules;
    }

    /**
     * @description Governance rulesets the API follows.
     */
    @MCChildElement(order = 4)
    public void setRules(List<CatalogResource.Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<CatalogResource.Workflow> getWorkflows() {
        return workflows;
    }

    /**
     * @description Workflows, such as Arazzo documents, that use the API.
     */
    @MCChildElement(order = 5)
    public void setWorkflows(List<CatalogResource.Workflow> workflows) {
        this.workflows = List.copyOf(workflows);
    }
}
