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

import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.createProxy;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static org.junit.jupiter.api.Assertions.*;

class APIProxyTest {

    @Test
    void port() {
        var p = new APIProxy();
        p.setKey(new APIProxyKey(80));
        assertTrue(p.getName().contains(":80"));
    }

    @Test
    void getNameWithName() {
        var p = new APIProxy();
        p.setName("Wonderproxy");
        assertEquals("Wonderproxy",p.getName());
    }

    @Test
    void getName() {
        var p = new APIProxy();
        var key = new APIProxyKey(80);
        key.setHost("localhost");
        key.setMethod("POST");
        key.setPath("/foo");
        p.setKey(key);
//        System.out.println("p.getName() = " + p.getName());
        assertEquals("localhost:80 POST /foo", p.getName());
    }

    @Test
    void getNameWithTest() {
        var p = new APIProxy();
        p.setTest("header.ContentType == 'text/plain'");
        p.init();
        assertEquals("0.0.0.0:80 header.ContentType == 'text/plain'",p.getName());
    }

    @Test
    void assignOpenAPIName_singleAPI() {
        var p = new APIProxy();
        p.apiRecords = Map.of("id1", new OpenAPIRecord(new OpenAPI().info(new Info().title("Test-API")), new OpenAPISpec()));
        p.setName(p.assignOpenAPIName());
        assertEquals("Test-API", p.getName());
    }

    @Test
    void assignOpenAPIName_multipleAPIs() {
        var p = new APIProxy();
        p.apiRecords = Map.of(
                "id1", new OpenAPIRecord(new OpenAPI().info(new Info().title("Test-API")), new OpenAPISpec()),
                "id2", new OpenAPIRecord(new OpenAPI().info(new Info().title("Test-API")), new OpenAPISpec())
        );
        p.setName(p.assignOpenAPIName());
        assertEquals("Test-API +1 more", p.getName());
    }

    @Test
    void xApiId() {
        var p = new APIProxy();
        p.apiRecords = Map.of(
                "id1", new OpenAPIRecord(new OpenAPI().info(new Info().title("Test-API")), new OpenAPISpec()),
                "id2", new OpenAPIRecord(new OpenAPI().info(new Info().title("Test-API")), new OpenAPISpec())
        );
        p.apiRecords.values().iterator().next().getApi().getInfo().addExtension("x-api-id", "Foo");
        p.setName(p.assignOpenAPIName());
        assertEquals("Foo", p.getName());
    }

    @Test
    void pathThatIsNoValidTemplateFailsAtStartupWhenValidating() {
        final var spec = invalidPathTemplateSpec();
        spec.setValidateRequests(YES);
        final var e = assertThrows(ConfigurationException.class, () -> createProxy(new DummyTestRouter(), spec));
        assertTrue(e.getMessage().contains("/items/{id/x}"));
        assertTrue(e.getMessage().contains("Invalid Path Template"));
    }

    @Test
    void pathThatIsNoValidTemplateIsIgnoredWithoutValidation() {
        final var proxy = createProxy(new DummyTestRouter(), invalidPathTemplateSpec());
        final var rec = proxy.getApiRecords().values().iterator().next();
        assertNull(proxy.getValidator(rec));
    }

    @Test
    void validatorIsCreatedWhenValidatingResponses() {
        final var spec = invalidPathTemplateSpec();
        spec.location = getPathFromResource("openapi/specs/customers.yml");
        spec.setValidateResponses(YES);
        final var proxy = createProxy(new DummyTestRouter(), spec);
        assertNotNull(proxy.getValidator(proxy.getApiRecords().values().iterator().next()));
    }

    private static OpenAPISpec invalidPathTemplateSpec() {
        final var spec = new OpenAPISpec();
        spec.location = getPathFromResource("openapi/specs/invalid-path-template.yml");
        return spec;
    }
}
