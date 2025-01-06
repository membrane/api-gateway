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
import com.predic8.membrane.core.util.*;
import io.swagger.v3.oas.models.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.NO;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class APIProxyOpenAPITest {

    private static final String REQUESTS = "requests";
    private static final String RESPONSES = "responses";
    private static final String SECURITY = "security";
    private static final String DETAILS = "details";

    Router router;

    @BeforeEach
    public void setUp() {
        router = new Router();
    }

    @Test
    public void noOptionsNoExtensions() throws Exception {
        Map<String,Object> xValidation = getXValidation(getSpec("no-extensions.yml"));
        assertNotNull(xValidation);
        assertFalse(isValidateRequests(xValidation));
        assertFalse(isValidateResponses(xValidation));
        assertFalse(isValidateSecurity(xValidation));
        assertTrue(isDetails(xValidation));
    }

    @Test
    public void validationRequestNoExtensions() throws Exception {
        OpenAPISpec spec = getSpec("no-extensions.yml");
        spec.validateRequests = YES;

        Map<String,Object> xValidation = getXValidation(spec);
        assertNotNull(xValidation);
        assertTrue(isValidateRequests(xValidation));
        assertFalse(isValidateResponses(xValidation));
        assertFalse(isValidateSecurity(xValidation));
        assertTrue(isDetails(xValidation));
    }

    @Test
    public void validationResponsesNoExtensions() throws Exception {
        OpenAPISpec spec = getSpec("no-extensions.yml");
        spec.validateResponses = YES;
        spec.validateSecurity = NO;

        Map<String,Object> xValidation = getXValidation(spec);
        assertNotNull(xValidation);
        assertFalse(isValidateRequests(xValidation));
        assertTrue(isValidateResponses(xValidation));
        assertFalse(isValidateSecurity(xValidation));
        assertTrue(isDetails(xValidation));
    }

    @Test
    public void validationAllNoExtensions() throws Exception {
        OpenAPISpec spec = getSpec("no-extensions.yml");
        spec.validateRequests = YES;
        spec.validateResponses = YES;

        Map<String,Object> xValidation = getXValidation(spec);
        assertNotNull(xValidation);
        assertTrue(isValidateRequests(xValidation));
        assertTrue(isValidateResponses(xValidation));
        assertFalse(isValidateSecurity(xValidation));
        assertTrue(isDetails(xValidation));
    }

    @Test
    public void requestsExtensions() throws Exception {
        Map<String,Object> xValidation = getXValidation(getSpec("validate-requests-extensions.yml"));
        assertNotNull(xValidation);
        assertTrue(isValidateRequests(xValidation));
        assertFalse(isValidateResponses(xValidation));
        assertFalse(isValidateSecurity(xValidation));
    }

    @Test
    public void securityExtension() throws Exception {
        Map<String,Object> xValidation = getXValidation(getSpec("validate-only-security-extensions.yml"));
        assertNotNull(xValidation);
        assertTrue(isValidateRequests(xValidation));
        assertFalse(isValidateResponses(xValidation));
        assertTrue(isValidateSecurity(xValidation));
    }

    @Test
    public void responsesExtensions() throws Exception {
        Map<String,Object> xValidation = getXValidation(getSpec("/validate-responses-extensions.yml"));
        assertNotNull(xValidation);
        assertFalse(isValidateRequests(xValidation));
        assertTrue(isValidateResponses(xValidation));
        assertFalse(isValidateSecurity(xValidation));
        assertNull(xValidation.get(DETAILS));
    }

    @Test
    public void validationRequestNoDetailsNoExtensions() throws Exception {
        OpenAPISpec spec = getSpec("no-extensions.yml");
        spec.validateRequests = YES;
        spec.validationDetails = NO;

        Map<String,Object> xValidation = getXValidation(spec);
        assertNotNull(xValidation);
        assertTrue(isValidateRequests(xValidation));
        assertFalse(isValidateResponses(xValidation));
        assertFalse(isValidateSecurity(xValidation));
        assertFalse(isDetails(xValidation));
    }

    @Test
    public void validationDetailsFalseExtensions() throws Exception {

        OpenAPISpec spec = getSpec("validation-details-false-extensions.yml");
        spec.validateRequests = YES;

        APIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());

        for (OpenAPIRecord rec: proxy.apiRecords.values()) {
            assertNotNull(rec.api.getExtensions());

            @SuppressWarnings("unchecked")
            Map<String,Object> xValidation = (Map<String, Object>) rec.api.getExtensions().get(X_MEMBRANE_VALIDATION);
            assertNotNull(xValidation);
            assertTrue(isValidateRequests(xValidation));
            assertFalse(isValidateResponses(xValidation));
            assertFalse(isValidateSecurity(xValidation));
            assertFalse(isDetails(xValidation));
        }
    }

    @Test
    public void multipleOpenAPIsWithTheSamePath() throws Exception {

        APIProxy api = new APIProxy();
        api.setName("TestAPI");
        api.init(router);
        api.setSpecs(List.of(extracted("src/test/resources/openapi/specs/paths/api-a-path-foo.yml"),
                extracted("src/test/resources/openapi/specs/paths/api-b-path-foo.yml")));

        assertThrows(ConfigurationException.class, api::init);
    }

    @Test
    public void oneOpenAPIWithMultipleServerUrlsSharingTheSamePath() throws Exception {

        APIProxy api = new APIProxy();
        api.setName("TestAPI");
        api.init(router);
        api.setSpecs(List.of(extracted("api-c-multiple-server-urls.yml")));
    }

    @Test
    void wrongLocationPath() {
        APIProxy api = new APIProxy();
        api.setName("TestAPI");
        api.setSpecs(List.of(new OpenAPISpec() {{
            location = "file:afdasfdasfsaf";
        }}));
        assertThrows(ConfigurationException.class, () -> api.init(router));
    }

    private OpenAPISpec extracted(String location) {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = location;
        return spec;
    }

    @NotNull
    private OpenAPI getOpenAPI(OpenAPISpec spec) throws Exception {
        APIProxy proxy = createProxy(router, spec);
        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = getSingleOpenAPIRecord(proxy.apiRecords).api;
        assertNotNull(api.getExtensions());
        return api;
    }

    private static Boolean isValidateSecurity(Map<String, Object> xValidation) {
        return (Boolean) xValidation.get(SECURITY);
    }

    private static Boolean isDetails(Map<String, Object> xValidation) {
        return (Boolean) xValidation.get(DETAILS);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getXValidation(OpenAPISpec spec) throws Exception {
        return (Map<String, Object>) getOpenAPI(spec).getExtensions().get(X_MEMBRANE_VALIDATION);
    }

    private static Boolean isValidateResponses(Map<String, Object> xValidation) {
        return (Boolean) xValidation.get(RESPONSES);
    }

    @NotNull
    private static OpenAPISpec getSpec(String location) {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/openapi-proxy/" + location;
        return spec;
    }

    private static Boolean isValidateRequests(Map<String, Object> xValidation) {
        return (Boolean) xValidation.get(REQUESTS);
    }
}