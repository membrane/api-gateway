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

import java.time.*;

import static com.predic8.membrane.core.util.DateAndTimeUtil.*;

/**
 * @description Metadata about the API that API catalogs publish.
 */
@MCElement(name = "info", component = false)
public class ProxyInfo {

    private ProxyContact contact;
    private LocalDate created;
    private LocalDate modified;

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

    public LocalDate getCreated() {
        return created;
    }

    /**
     * @description Day the API was created, as an ISO date.
     * @example 2026-01-15
     */
    @MCAttribute
    public void setCreated(String created) {
        this.created = parseIsoDate("info", "created", created);
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
        this.modified = parseIsoDate("info", "modified", modified);
    }
}
