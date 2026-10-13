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

/**
 * Metadata of the collection as a whole, which APIs.json and the Membrane format carry and an
 * RFC 9727 linkset does not. The dates only APIs.json carries are handed to its renderer directly.
 *
 * @param id          identifier of the collection, the second half of its <code>aid</code>
 * @param name        human readable name of the collection
 * @param description human readable description of the collection
 */
public record CollectionInfo(String id, String name, String description) {

    /**
     * Unique identifier of the collection, <code>&lt;domain&gt;:&lt;id&gt;</code>.
     */
    public String aid(String domain) {
        return domain + ":" + id;
    }
}
