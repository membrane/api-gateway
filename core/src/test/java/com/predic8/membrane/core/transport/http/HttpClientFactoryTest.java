/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.transport.http.client.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientFactoryTest {

    HttpClientFactory f = new HttpClientFactory(null);

    @Test
    void same() {
        assertSame(f.createClient(getConfig(1)), f.createClient(getConfig(1)));
    }

    @Test
    void different() {
        assertNotSame(f.createClient(getConfig(1)), f.createClient(getConfig(2)));
    }

    private static @NotNull HttpClientConfiguration getConfig(int retries) {
        return new HttpClientConfiguration() {{
           setRetryHandler(new RetryHandler() {{
               setRetries(retries);
           }});
        }};
    }
}