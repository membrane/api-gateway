package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.*;
import io.swagger.v3.oas.models.*;
import org.junit.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIProxy.X_MEMBRANE_VALIDATION;
import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static java.util.Collections.singletonList;
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
        OpenAPI api = proxy.apiRecords.get(0).api;

        assertNotNull(api.getExtensions());

        @SuppressWarnings("unchecked")
        Map<String,Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        assertNotNull(xValidation);
        assertFalse((Boolean) xValidation.get("requests"));
        assertFalse((Boolean) xValidation.get("responses"));
        assertTrue((Boolean) xValidation.get("validationDetails"));
    }

    @Test
    public void validationRequestNoExtensions() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/openapi-proxy/no-extensions.yml";
        spec.validateRequests = true;

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = proxy.apiRecords.get(0).api;

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
        spec.validateResponses = true;

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = proxy.apiRecords.get(0).api;

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
        spec.validateRequests = true;
        spec.validateResponses = true;

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = proxy.apiRecords.get(0).api;

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
        OpenAPI api = proxy.apiRecords.get(0).api;

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
        OpenAPI api = proxy.apiRecords.get(0).api;

        assertNotNull(api.getExtensions());

        @SuppressWarnings("unchecked")
        Map<String,Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        assertNotNull(xValidation);
        assertFalse((Boolean) xValidation.get("requests"));
        assertTrue((Boolean) xValidation.get("responses"));
        assertNull(xValidation.get("validationDetails"));
    }

    @Test
    public void validationRequestNoDetailsNoExtensions() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/openapi-proxy/no-extensions.yml";
        spec.validateRequests = true;
        spec.validationDetails = false;

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = proxy.apiRecords.get(0).api;

        assertNotNull(api.getExtensions());

        @SuppressWarnings("unchecked")
        Map<String,Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        assertNotNull(xValidation);
        assertTrue((Boolean) xValidation.get("requests"));
        assertFalse((Boolean) xValidation.get("responses"));
        assertFalse((Boolean) xValidation.get("validationDetails"));
    }

    @Test
    public void validationDetailsFalseExtensions() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/openapi-proxy/validation-details-false-extensions.yml";
        spec.validateRequests = true;

        OpenAPIProxy proxy = createProxy(router,spec);

        assertEquals(1, proxy.apiRecords.size());
        OpenAPI api = proxy.apiRecords.get(0).api;

        assertNotNull(api.getExtensions());

        @SuppressWarnings("unchecked")
        Map<String,Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        assertNotNull(xValidation);
        assertTrue((Boolean) xValidation.get("requests"));
        assertFalse((Boolean) xValidation.get("responses"));
        assertFalse((Boolean) xValidation.get("validationDetails"));
    }
}