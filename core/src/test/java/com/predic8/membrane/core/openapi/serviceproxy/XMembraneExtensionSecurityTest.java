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

import com.predic8.membrane.core.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.URISyntaxException;
import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static org.junit.jupiter.api.Assertions.*;

public class XMembraneExtensionSecurityTest {

    OpenAPIPublisherInterceptor interceptor;

    @BeforeEach
    void setUp() throws IOException, ClassNotFoundException, URISyntaxException {
        Router router = new Router();
        router.setBaseLocation("");
        OpenAPIRecordFactory factory = new OpenAPIRecordFactory(router);
        OpenAPISpec spec = new OpenAPISpec();
        spec.setLocation("src/test/resources/openapi/openapi-proxy/validate-security-extensions.yml");
        Map<String,OpenAPIRecord> records = factory.create(Collections.singletonList(spec));

        interceptor = new OpenAPIPublisherInterceptor(records);
    }

    @Test
    void checkXMembraneIdIsSet() {
        assertNotNull(interceptor.apis.get("validate-security-api-v1-0"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void readingExtensionsForValidation() {
        OpenAPIRecord rec = interceptor.apis.get("validate-security-api-v1-0");
        assertEquals(ASINOPENAPI,rec.spec.validateSecurity);
        var validationExtension = (Map<String, Boolean>) rec.api.getExtensions().get(X_MEMBRANE_VALIDATION);
        assertEquals(true,validationExtension.get(REQUESTS));
        assertEquals(true,validationExtension.get(RESPONSES));
        assertEquals(true,validationExtension.get(SECURITY));
    }
}