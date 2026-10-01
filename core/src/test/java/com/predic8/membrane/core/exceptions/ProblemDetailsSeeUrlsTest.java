/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exceptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.EchoInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.interceptor.administration.AdminRESTInterceptor;
import com.predic8.membrane.core.interceptor.administration.DynamicAdminPageInterceptor;
import com.predic8.membrane.core.interceptor.jwt.JwtSignInterceptor;
import com.predic8.membrane.core.interceptor.kubernetes.KubernetesValidationInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2ClientInterceptor;
import com.predic8.membrane.core.interceptor.oauth2client.FlowInitiator;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityInterceptor;
import com.predic8.membrane.core.interceptor.stomp.STOMPClient;
import com.predic8.membrane.core.interceptor.templating.TemplateInterceptor;
import com.predic8.membrane.core.interceptor.ws_addressing.WsaEndpointRewriterInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIInterceptor;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static org.junit.jupiter.api.Assertions.*;

public class ProblemDetailsSeeUrlsTest {

    private static final ObjectMapper om = new ObjectMapper();

    @Test
    void testAffectedPluginsDoNotUseFullyQualifiedClassNames() throws Exception {
        List<Interceptor> interceptors = List.of(
                new AccessControlInterceptor(),
                new AdminRESTInterceptor(),
                new DynamicAdminPageInterceptor(),
                new EchoInterceptor(),
                new FlowInitiator(),
                new JwtSignInterceptor(),
                new KubernetesValidationInterceptor(),
                new STOMPClient(),
                new WsSecurityInterceptor(),
                new WsaEndpointRewriterInterceptor()
        );

        for (Interceptor i : interceptors) {
            String displayName = i.getDisplayName();
            assertFalse(displayName.contains("com.predic8"),
                    "Display name should not contain package name: " + displayName);
            assertFalse(displayName.contains("."),
                    "Display name should not contain dot: " + displayName);

            Response r = user(false, displayName)
                    .title("Test")
                    .build();

            JsonNode json = om.readTree(r.getBodyAsStringDecoded());
            String see = json.get(SEE).asText();
            assertTrue(see.startsWith("https://membrane-api.io/problems/"),
                    "See URL should have valid base: " + see);
            assertFalse(see.contains("com.predic8"),
                    "See URL should not contain package name: " + see);
        }
    }

    @Test
    void testExpectedComponentNames() {
        assertEquals("accessControl", new AccessControlInterceptor().getDisplayName());
        assertEquals("adminREST", new AdminRESTInterceptor().getDisplayName());
        assertEquals("dynamicAdminPage", new DynamicAdminPageInterceptor().getDisplayName());
        assertEquals("echo", new EchoInterceptor().getDisplayName());
        assertEquals("flowInitiator", new FlowInitiator().getDisplayName());
        assertEquals("jwtSign", new JwtSignInterceptor().getDisplayName());
        assertEquals("kubernetesValidation", new KubernetesValidationInterceptor().getDisplayName());
        assertEquals("stompClient", new STOMPClient().getDisplayName());
        assertEquals("wsSecurity", new WsSecurityInterceptor().getDisplayName());
        assertEquals("wsaEndpointRewriter", new WsaEndpointRewriterInterceptor().getDisplayName());
    }

    @Test
    void testTemplateDoubledSegmentFixed() throws Exception {
        TemplateInterceptor ti = new TemplateInterceptor();
        ti.setSrc("<%= 1 / 0 %>");
        ti.init(new DefaultRouter());

        Exchange exc = post("/test").body("").buildExchange();
        ti.handleRequest(exc);

        Response res = exc.getResponse();
        assertNotNull(res);
        JsonNode json = om.readTree(res.getBodyAsStringDecoded());
        assertTrue(json.hasNonNull(SEE), "ProblemDetails must contain see field");
        String see = json.get(SEE).asText();
        assertFalse(see.contains("template/template"), "See URL should not contain doubled template/template: " + see);
        assertTrue(see.startsWith("https://membrane-api.io/problems/internal/template"),
                "See URL should match internal/template: " + see);
    }

    @Test
    void testOpenAPIDoubledSegmentFixed() throws Exception {
        OpenAPIInterceptor oai = new OpenAPIInterceptor();
        Response r = user(false, oai.getDisplayName())
                .flow(RESPONSE)
                .detail("Could not parse OpenAPI")
                .build();

        JsonNode json = om.readTree(r.getBodyAsStringDecoded());
        assertTrue(json.hasNonNull(SEE));
        String see = json.get(SEE).asText();
        assertFalse(see.contains("openapi/openapi"), "See URL should not contain doubled openapi/openapi: " + see);
        assertEquals("https://membrane-api.io/problems/user/openapi/response", see);
    }

    @Test
    void testOAuth2CollisionResolved() throws Exception {
        OAuth2ClientInterceptor client = new OAuth2ClientInterceptor();
        OAuth2Resource2Interceptor resource = new OAuth2Resource2Interceptor();

        assertNotEquals(client.getDisplayName(), resource.getDisplayName(),
                "Display names of OAuth2 client and resource should not be identical");

        Response rClient = user(false, client.getDisplayName()).build();
        Response rResource = user(false, resource.getDisplayName()).build();

        String seeClient = om.readTree(rClient.getBodyAsStringDecoded()).get(SEE).asText();
        String seeResource = om.readTree(rResource.getBodyAsStringDecoded()).get(SEE).asText();

        assertNotEquals(seeClient, seeResource,
                "See URLs for OAuth2ClientInterceptor and OAuth2Resource2Interceptor must not collide");
    }

    @Test
    void testSubclassInheritsMCElementName() {
        Interceptor anonymous = new EchoInterceptor() {};
        assertEquals("echo", anonymous.getDisplayName());
    }
}
