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

/**
 * @description Person or team responsible for the API.
 */
@MCElement(name = "contact", component = false)
public class ProxyContact {

    private String name;
    private String email;
    private String url;

    public String getName() {
        return name;
    }

    /**
     * @description Name of the person or team.
     * @example Platform Team
     */
    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    /**
     * @description Email address to contact.
     * @example platform@example.com
     */
    @MCAttribute
    public void setEmail(String email) {
        this.email = email;
    }

    public String getUrl() {
        return url;
    }

    /**
     * @description Web page with contact details.
     * @example https://example.com/platform
     */
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }
}
