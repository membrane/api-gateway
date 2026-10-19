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

import java.util.*;

/**
 * A typed link or inline value an API catalog publishes about an API or a collection, such as its
 * pricing page, an AI prompt or a governance ruleset. One subclass per list, because XML tells the
 * lists apart by element name.
 */
public abstract class CatalogResource {

    private String type;
    private String name;
    private String description;
    private String mediaType;
    private String url;
    private String data;
    private List<String> tags = List.of();

    /**
     * APIs.json requires a URL or the data itself.
     */
    public void validate() {
        if (url == null && data == null)
            throw new ConfigurationException("The %s \"%s\" needs a url or data.".formatted(elementName(), type));
    }

    private String elementName() {
        final var element = getClass().getAnnotation(MCElement.class);
        return element == null ? getClass().getSimpleName() : element.name();
    }

    public String getType() {
        return type;
    }

    /**
     * @description Type of the resource, preferably one of the keywords APIs.json reserves, such as
     * <code>Documentation</code>, <code>Signup</code>, <code>Pricing</code>, <code>AgentPrompt</code>,
     * <code>SpectralRules</code> or <code>Arazzo</code>.
     * @example Pricing
     */
    @Required
    @MCAttribute
    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    /**
     * @description Display name.
     * @example Plans and pricing
     */
    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * @description Human readable description.
     * @example Free and paid plans of the API
     */
    @MCAttribute
    public void setDescription(String description) {
        this.description = description;
    }

    public String getMediaType() {
        return mediaType;
    }

    /**
     * @description IANA media type of the resource.
     * @example text/html
     */
    @MCAttribute
    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getUrl() {
        return url;
    }

    /**
     * @description URL of the resource. Either this or <code>data</code> is required.
     * @example https://example.com/pricing
     */
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    public String getData() {
        return data;
    }

    /**
     * @description The resource itself, inline. Either this or <code>url</code> is required.
     * @example Summarize the orders of the last week.
     */
    @MCAttribute
    public void setData(String data) {
        this.data = data;
    }

    public List<String> getTags() {
        return tags;
    }

    /**
     * @description Labels for the resource.
     * @example [billing]
     */
    @MCChildElement(allowForeign = true)
    public void setTags(List<String> tags) {
        this.tags = List.copyOf(tags);
    }

    /**
     * @description A technical or operational property of an API, such as its pricing page,
     * changelog or status page.
     */
    @MCElement(name = "property", component = false, id = "catalog-property")
    public static class Property extends CatalogResource {
    }

    /**
     * @description A property shared by every API of the collection, such as the signup page or
     * the terms of service.
     */
    @MCElement(name = "common", component = false, id = "catalog-common")
    public static class Common extends CatalogResource {
    }

    /**
     * @description An AI prompt, for example of type <code>Prompt</code>, <code>AgentPrompt</code>
     * or <code>PromptTemplate</code>.
     */
    @MCElement(name = "prompt", component = false, id = "catalog-prompt")
    public static class Prompt extends CatalogResource {
    }

    /**
     * @description A governance ruleset, for example of type <code>SpectralRules</code>,
     * <code>Rules</code> or <code>GovernanceRules</code>.
     */
    @MCElement(name = "rule", component = false, id = "catalog-rule")
    public static class Rule extends CatalogResource {
    }

    /**
     * @description A workflow, for example of type <code>Arazzo</code>,
     * <code>ServerlessWorkflow</code> or <code>Workflow</code>.
     */
    @MCElement(name = "workflow", component = false, id = "catalog-workflow")
    public static class Workflow extends CatalogResource {
    }
}
