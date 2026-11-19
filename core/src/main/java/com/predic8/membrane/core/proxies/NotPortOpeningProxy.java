/* Copyright 2023 predic8 GmbH, www.predic8.com

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

/**
 * An API that opens no port. It gets its exchanges in other ways: from other APIs through <code>internal://</code>, or
 * through an inbound of its own, e.g. messages from a broker or a schedule.
 */
public interface NotPortOpeningProxy {

    /**
     * Stops the API's own inbound, so that it starts no further exchanges. Exchanges in progress may finish. Called
     * when the router stops or reloads its configuration, like closing the ports stops the inbound of the other APIs,
     * and before the HTTP clients are closed. Can be called more than once.
     */
    default void stopInbound() {
    }
}
