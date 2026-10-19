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

import java.time.*;
import java.util.*;

/**
 * The members of an APIs.json collection that come from the configuration rather than from the
 * deployed APIs. Optional members are null or empty when not configured, and are then left out.
 *
 * @param id       second half of the collection's <code>aid</code>, the domain being the first
 * @param type     <code>Index</code>, <code>Collection</code> or <code>Blueprint</code>
 * @param created  day the collection was created, a member APIs.json makes mandatory
 * @param modified day the collection last changed, a member APIs.json makes mandatory
 */
public record ApisJsonCollection(String id, String type, String kind, String image, String position, String access,
                                 LocalDate created, LocalDate modified, List<CatalogEntry.Contact> maintainers,
                                 List<CatalogEntry.Resource> common, List<CatalogEntry.Resource> prompts,
                                 List<CatalogEntry.Resource> rules, List<CatalogEntry.Resource> workflows,
                                 List<CatalogEntry.Reference> overlays, List<CatalogEntry.Reference> include,
                                 List<CatalogEntry.Reference> networks) {

    public ApisJsonCollection {
        maintainers = List.copyOf(maintainers);
        common = List.copyOf(common);
        prompts = List.copyOf(prompts);
        rules = List.copyOf(rules);
        workflows = List.copyOf(workflows);
        overlays = List.copyOf(overlays);
        include = List.copyOf(include);
        networks = List.copyOf(networks);
    }

    public static Builder builder(String id, LocalDate created, LocalDate modified) {
        return new Builder(id, created, modified);
    }

    public static class Builder {

        private final String id;
        private final LocalDate created;
        private final LocalDate modified;
        private String type;
        private String kind;
        private String image;
        private String position;
        private String access;
        private List<CatalogEntry.Contact> maintainers = List.of();
        private List<CatalogEntry.Resource> common = List.of();
        private List<CatalogEntry.Resource> prompts = List.of();
        private List<CatalogEntry.Resource> rules = List.of();
        private List<CatalogEntry.Resource> workflows = List.of();
        private List<CatalogEntry.Reference> overlays = List.of();
        private List<CatalogEntry.Reference> include = List.of();
        private List<CatalogEntry.Reference> networks = List.of();

        private Builder(String id, LocalDate created, LocalDate modified) {
            this.id = id;
            this.created = created;
            this.modified = modified;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public Builder image(String image) {
            this.image = image;
            return this;
        }

        public Builder position(String position) {
            this.position = position;
            return this;
        }

        public Builder access(String access) {
            this.access = access;
            return this;
        }

        public Builder maintainers(List<CatalogEntry.Contact> maintainers) {
            this.maintainers = maintainers;
            return this;
        }

        public Builder common(List<CatalogEntry.Resource> common) {
            this.common = common;
            return this;
        }

        public Builder prompts(List<CatalogEntry.Resource> prompts) {
            this.prompts = prompts;
            return this;
        }

        public Builder rules(List<CatalogEntry.Resource> rules) {
            this.rules = rules;
            return this;
        }

        public Builder workflows(List<CatalogEntry.Resource> workflows) {
            this.workflows = workflows;
            return this;
        }

        public Builder overlays(List<CatalogEntry.Reference> overlays) {
            this.overlays = overlays;
            return this;
        }

        public Builder include(List<CatalogEntry.Reference> include) {
            this.include = include;
            return this;
        }

        public Builder networks(List<CatalogEntry.Reference> networks) {
            this.networks = networks;
            return this;
        }

        public ApisJsonCollection build() {
            return new ApisJsonCollection(id, type, kind, image, position, access, created, modified, maintainers,
                    common, prompts, rules, workflows, overlays, include, networks);
        }
    }
}
