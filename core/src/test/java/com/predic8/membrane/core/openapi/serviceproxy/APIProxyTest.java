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
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.NO;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class APIProxyTest {

    Router router;

    @BeforeEach
    public void setUp() {
        router = new Router();
    }

    @Test
    public void noOptionsNoExtensions() throws Exception {

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/openapi-proxy/no-extensions.yml";

        APIProxy proxy = createProxy(router,spec);

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

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/openapi-proxy/no-extensions.yml";
        spec.validateRequests = YES;

        APIProxy proxy = createProxy(router,spec);

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

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/openapi-proxy/no-extensions.yml";
        spec.validateResponses = YES;

        APIProxy proxy = createProxy(router,spec);

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

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/openapi-proxy/no-extensions.yml";
        spec.validateRequests = YES;
        spec.validateResponses = YES;

        APIProxy proxy = createProxy(router,spec);

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

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/openapi-proxy/validate-requests-extensions.yml";

        APIProxy proxy = createProxy(router,spec);

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

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/openapi-proxy/validate-responses-extensions.yml";

        APIProxy proxy = createProxy(router,spec);

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

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/openapi-proxy/no-extensions.yml";
        spec.validateRequests = YES;
        spec.validationDetails = NO;

        APIProxy proxy = createProxy(router,spec);

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

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/openapi-proxy/validation-details-false-extensions.yml";
        spec.validateRequests = YES;

        APIProxy proxy = createProxy(router,spec);

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

    @Test
    public void multipleOpenAPIsWithTheSamePath() throws Exception {

        APIProxy api = new APIProxy();
        api.setName("TestAPI");
        api.init(router);
        api.setSpecs(List.of(extracted("src/test/resources/openapi/specs/paths/api-a-path-foo.yml"),
                extracted("src/test/resources/openapi/specs/paths/api-b-path-foo.yml")));

        assertThrows(DuplicatePathException.class, api::init);
    }

    @Test
    public void oneOpenAPIWithMultipleServerUrlsSharingTheSamePath() throws Exception {

        APIProxy api = new APIProxy();
        api.setName("TestAPI");
        api.init(router);
        api.setSpecs(List.of(extracted("api-c-multiple-server-urls.yml")));
    }

    private OpenAPISpec extracted(String location) {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = location;
        return spec;
    }
}