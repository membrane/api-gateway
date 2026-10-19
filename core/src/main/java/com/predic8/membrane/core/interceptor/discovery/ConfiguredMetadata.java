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

import com.predic8.membrane.core.proxies.*;

import java.util.*;

/**
 * Turns catalog metadata from the configuration into the immutable records the renderers read,
 * so that no renderer depends on a mutable configuration object.
 */
final class ConfiguredMetadata {

    private ConfiguredMetadata() {
    }

    static CatalogEntry.Contact contact(ProxyContact contact) {
        return new CatalogEntry.Contact(contact.getName(), contact.getEmail(), contact.getUrl(),
                contact.getOrganization(), contact.getAddress(), contact.getPhone(), contact.getTwitter(),
                contact.getGithub(), contact.getPhoto(), contact.getVCard());
    }

    static List<CatalogEntry.Contact> contacts(List<ProxyContact> contacts) {
        return contacts.stream().map(ConfiguredMetadata::contact).toList();
    }

    static List<CatalogEntry.Resource> resources(List<? extends CatalogResource> resources) {
        return resources.stream().map(resource -> new CatalogEntry.Resource(resource.getType(), resource.getName(),
                resource.getDescription(), resource.getMediaType(), resource.getUrl(), resource.getData(),
                resource.getTags())).toList();
    }

    static List<CatalogEntry.Reference> overlays(List<CatalogOverlay> overlays) {
        return overlays.stream().map(overlay -> new CatalogEntry.Reference(overlay.getType(), overlay.getUrl())).toList();
    }

    static List<CatalogEntry.Reference> references(List<? extends CatalogReference> references) {
        return references.stream().map(reference -> new CatalogEntry.Reference(reference.getName(), reference.getUrl())).toList();
    }

    static CatalogEntry.Details details(ProxyInfo info) {
        if (info == null)
            return CatalogEntry.Details.NONE;
        return new CatalogEntry.Details(info.getImage(), info.getCreated(), info.getModified(),
                resources(info.getProperties()), overlays(info.getOverlays()), resources(info.getPrompts()),
                resources(info.getRules()), resources(info.getWorkflows()));
    }
}
