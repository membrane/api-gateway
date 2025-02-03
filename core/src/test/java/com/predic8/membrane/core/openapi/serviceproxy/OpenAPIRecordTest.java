/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.proxies.NullProxy;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.BeforeEach;

class OpenAPIRecordTest {

    Exchange get = new Exchange(null);

    @BeforeEach
    void setUp() {
        Router router = new Router();
        router.setUriFactory(new URIFactory());
        router.setBaseLocation("");

        get.setRequest(new Request.Builder().method("GET").build());
        get.setProxy(new NullProxy());
        get.setOriginalHostHeader("api.predic8.de:80");
    }
}