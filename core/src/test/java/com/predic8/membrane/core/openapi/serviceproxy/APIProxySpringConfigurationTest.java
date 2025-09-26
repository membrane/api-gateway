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
package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.apikey.*;
import com.predic8.membrane.core.interceptor.log.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static org.junit.jupiter.api.Assertions.*;

class APIProxySpringConfigurationTest extends AbstractProxySpringConfigurationTest {

    static final String publisherSeparate = """
            <api port="2000" name="bool-api">
                <openapi location="%s" validateSecurity="yes"/>
                <apiKey required = "false">
                    <headerExtractor name="X-Api-Key"/>
                </apiKey>
                <headerFilter>
                    <exclude>Origin</exclude>
                </headerFilter>
                <openapiPublisher/>
                <log/>
                <target url="https://api.predic8.de"/>
            </api>""".formatted(getPathFromResource("openapi/specs/boolean.yml"));

    static final String noPublisherNoOpenAPIInterceptor = """
            <api port="2000" name="bool-api">
                <openapi location="%s" validateSecurity="yes"/>
                <apiKey required = "false">
                    <headerExtractor name="X-Api-Key"/>
                </apiKey>
                <headerFilter>
                    <exclude>Origin</exclude>
                </headerFilter>
                <log/>
                <target url="https://api.predic8.de"/>
            </api>""".formatted(getPathFromResource("openapi/specs/boolean.yml"));


    @Test
    void interceptorSequenceFromSpringConfiguration() {
        Router router = startSpringContextAndReturnRouter(publisherSeparate);
        APIProxy ap = getApiProxy(router);
        assertEquals(4, ap.getFlow().size());
        assertInstanceOf(ApiKeysInterceptor.class, ap.getFlow().get(0));
        assertInstanceOf(HeaderFilterInterceptor.class, ap.getFlow().get(1));
        assertInstanceOf(OpenAPIPublisherInterceptor.class, ap.getFlow().get(2));
        assertInstanceOf(LogInterceptor.class, ap.getFlow().get(3));
    }

    @Test
    void interceptorSequenceAferInit() {
        Router router = startSpringContextAndReturnRouter(publisherSeparate);
        APIProxy ap = getApiProxy(router);
        ap.init(router);
        assertEquals(5, ap.getFlow().size());
        assertInstanceOf(OpenAPIInterceptor.class, ap.getFlow().get(0)); // Got added
        assertInstanceOf(ApiKeysInterceptor.class, ap.getFlow().get(1));
        assertInstanceOf(HeaderFilterInterceptor.class, ap.getFlow().get(2));
        assertInstanceOf(OpenAPIPublisherInterceptor.class, ap.getFlow().get(3));
        assertInstanceOf(LogInterceptor.class, ap.getFlow().get(4));
    }

    @Test
    void noPublisherNoOpenAPIInterceptor() {
        Router router = startSpringContextAndReturnRouter(noPublisherNoOpenAPIInterceptor);
        APIProxy ap = getApiProxy(router);
        ap.init(router);
        assertEquals(5, ap.getFlow().size());
        assertInstanceOf(OpenAPIPublisherInterceptor.class, ap.getFlow().get(0)); // Was added
        assertInstanceOf(OpenAPIInterceptor.class, ap.getFlow().get(1)); //
        assertInstanceOf(ApiKeysInterceptor.class, ap.getFlow().get(2));
        assertInstanceOf(HeaderFilterInterceptor.class, ap.getFlow().get(3));
        assertInstanceOf(LogInterceptor.class, ap.getFlow().get(4));
    }
}
