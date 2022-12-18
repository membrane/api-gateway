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
import io.swagger.v3.oas.models.*;
import org.junit.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIProxy.Spec.YesNoOpenAPIOption.NO;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIProxy.Spec.YesNoOpenAPIOption.YES;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIProxy.X_MEMBRANE_VALIDATION;
import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static com.predic8.membrane.core.openapi.util.TestUtils.getSingleOpenAPIRecord;
import static org.junit.Assert.*;

public class OpenAPIProxyTest {

    Router router;

    @Before
    public void setUp() {
        router = new Router();
    }

    @Test
    public void noOptionsNoExtensions() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/openapi-proxy/no-extensions.yml";

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = getSingleOpenAPIRecord(proxy.apiRecords).api;

        assertNotNull(api.getExtensions());

        @SuppressWarnings("unchecked")
        Map<String,Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        assertNotNull(xValidation);
        assertFalse((Boolean) xValidation.get("requests"));
        assertFalse((Boolean) xValidation.get("responses"));
        assertTrue((Boolean) xValidation.get("details"));
    }

    @Test
    public void validationRequestNoExtensions() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/openapi-proxy/no-extensions.yml";
        spec.validateRequests = YES;

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = getSingleOpenAPIRecord(proxy.apiRecords).api;

        assertNotNull(api.getExtensions());

        @SuppressWarnings("unchecked")
        Map<String,Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        assertNotNull(xValidation);
        assertTrue((Boolean) xValidation.get("requests"));
        assertFalse((Boolean) xValidation.get("responses"));
    }

    @Test
    public void validationResponsesNoExtensions() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/openapi-proxy/no-extensions.yml";
        spec.validateResponses = YES;

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = getSingleOpenAPIRecord(proxy.apiRecords).api;

        assertNotNull(api.getExtensions());

        @SuppressWarnings("unchecked")
        Map<String,Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        assertNotNull(xValidation);
        assertFalse((Boolean) xValidation.get("requests"));
        assertTrue((Boolean) xValidation.get("responses"));

    }

    @Test
    public void validationAllNoExtensions() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/openapi-proxy/no-extensions.yml";
        spec.validateRequests = YES;
        spec.validateResponses = YES;

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = getSingleOpenAPIRecord(proxy.apiRecords).api;

        assertNotNull(api.getExtensions());

        @SuppressWarnings("unchecked")
        Map<String,Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        assertNotNull(xValidation);
        assertTrue((Boolean) xValidation.get("requests"));
        assertTrue((Boolean) xValidation.get("responses"));
    }

    @Test
    public void requestsExtensions() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/openapi-proxy/validate-requests-extensions.yml";

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = getSingleOpenAPIRecord(proxy.apiRecords).api;

        assertNotNull(api.getExtensions());

        @SuppressWarnings("unchecked")
        Map<String,Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        assertNotNull(xValidation);
        assertTrue((Boolean) xValidation.get("requests"));
        assertFalse((Boolean) xValidation.get("responses"));
    }

    @Test
    public void responsesExtensions() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/openapi-proxy/validate-responses-extensions.yml";

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = getSingleOpenAPIRecord(proxy.apiRecords).api;

        assertNotNull(api.getExtensions());

        @SuppressWarnings("unchecked")
        Map<String,Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        assertNotNull(xValidation);
        assertFalse((Boolean) xValidation.get("requests"));
        assertTrue((Boolean) xValidation.get("responses"));
        assertNull(xValidation.get("details"));
    }

    @Test
    public void validationRequestNoDetailsNoExtensions() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/openapi-proxy/no-extensions.yml";
        spec.validateRequests = YES;
        spec.validationDetails = NO;

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = getSingleOpenAPIRecord(proxy.apiRecords).api;

        assertNotNull(api.getExtensions());

        @SuppressWarnings("unchecked")
        Map<String,Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        assertNotNull(xValidation);
        assertTrue((Boolean) xValidation.get("requests"));
        assertFalse((Boolean) xValidation.get("responses"));
        assertFalse((Boolean) xValidation.get("details"));
    }

    @Test
    public void validationDetailsFalseExtensions() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/openapi-proxy/validation-details-false-extensions.yml";
        spec.validateRequests = YES;

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());

        for (OpenAPIRecord rec: proxy.apiRecords.values()) {
            assertNotNull(rec.api.getExtensions());

            @SuppressWarnings("unchecked")
            Map<String,Object> xValidation = (Map<String, Object>) rec.api.getExtensions().get(X_MEMBRANE_VALIDATION);

            assertNotNull(xValidation);
            assertTrue((Boolean) xValidation.get("requests"));
            assertFalse((Boolean) xValidation.get("responses"));
            assertFalse((Boolean) xValidation.get("details"));
        }
    }
}