/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2.client.b2c;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.*;
import com.predic8.membrane.core.interceptor.oauth2client.*;
import com.predic8.membrane.core.interceptor.session.*;
import com.predic8.membrane.core.proxies.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.function.*;

import static com.predic8.membrane.core.RuleManager.RuleDefinitionSource.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

/**
 * A locally running Membrane with various B2C features to test (1 ServiceProxy per feature). Primarily:
 *
 * <ul>
 *     <li>an OAuth2Resource2 interceptor</li>
 *     <li>two FlowInitiator interceptors (one with and one without 'logout-before-flow' enabled)</li>
 *     <li>four RequireAuth interceptors in varying configurations</li>
 * </ul>
 * <p>
 * They all point back to the one oauth2resource2 interceptor instance managing sessions, token acquisitions and more.
 */
public class B2CMembrane {
    private final SessionManager sessionManager;
    private final B2CTestConfig tc;

    private final ObjectMapper om = new ObjectMapper();

    private HttpRouter oauth2Resource;
    private OAuth2Resource2Interceptor oAuth2Resource2Interceptor;

    public RequireAuth requireAuth;

    public B2CMembrane(B2CTestConfig tc, SessionManager sessionManager) {
        this.tc = tc;
        this.sessionManager = sessionManager;
    }

    public void init() {
        oauth2Resource = new HttpRouter();
        oauth2Resource.getTransport().setBacklog(10000);
        oauth2Resource.getTransport().setSocketTimeout(10000);
        oauth2Resource.setHotDeploy(false);
        oauth2Resource.getTransport().setConcurrentConnectionLimitPerIp(tc.limit);

        ServiceProxy sp1_oauth2resource2 = createOAuth2Resource2ServiceProxy();
        ServiceProxy sp2_flowInitiator_logoutBeforeFlow = createFlowInitiatorServiceProxy("/pe/", tc.peFlowId, true);
        ServiceProxy sp3_flowInitiator_noLogout = createFlowInitiatorServiceProxy("/pe2/", tc.pe2FlowId, false);
        sp1_oauth2resource2.init(oauth2Resource);
        ServiceProxy sp4_requireAuth = createRequireAuthServiceProxy(tc.api1Id, "/api/", ra -> {
            requireAuth = ra;
            ra.setScope("https://localhost/" + tc.api1Id + "/Read");
        });
        ServiceProxy sp5_requireAuth_AuthNotRequired = createRequireAuthServiceProxy(tc.api1Id, "/api-no-auth-needed/", ra -> {
            ra.setRequired(false);
            ra.setScope("https://localhost/" + tc.api1Id + "/Read");
        });
        ServiceProxy sp6_requireAuth_ErrorStatus403 = createRequireAuthServiceProxy(tc.api1Id, "/api-no-redirect/", ra -> {
            ra.setErrorStatus(403);
            ra.setScope("https://localhost/" + tc.api1Id + "/Read");
        });
        ServiceProxy sp7_requireAuth = createRequireAuthServiceProxy(tc.api2Id, "/api2/", ra -> ra.setScope("https://localhost/" + tc.api2Id + "/Read"));
        ServiceProxy sp8_afterLogout = createAfterLogoutServiceProxy();

        oauth2Resource.getRuleManager().addProxy(sp8_afterLogout, MANUAL);
        oauth2Resource.getRuleManager().addProxy(sp7_requireAuth, MANUAL);
        oauth2Resource.getRuleManager().addProxy(sp6_requireAuth_ErrorStatus403, MANUAL);
        oauth2Resource.getRuleManager().addProxy(sp5_requireAuth_AuthNotRequired, MANUAL);
        oauth2Resource.getRuleManager().addProxy(sp4_requireAuth, MANUAL);
        oauth2Resource.getRuleManager().addProxy(sp3_flowInitiator_noLogout, MANUAL);
        oauth2Resource.getRuleManager().addProxy(sp2_flowInitiator_logoutBeforeFlow, MANUAL);
        oauth2Resource.getRuleManager().addProxy(sp1_oauth2resource2, MANUAL);
        oauth2Resource.start();

    }

    public void stop() {
        oauth2Resource.stop();
    }

    private ServiceProxy createOAuth2Resource2ServiceProxy() {
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(tc.clientPort), null, 99999);

        OAuth2Resource2Interceptor oAuth2ResourceInterceptor = new OAuth2Resource2Interceptor();
        this.oAuth2Resource2Interceptor = oAuth2ResourceInterceptor;
        if (sessionManager != null)
            oAuth2ResourceInterceptor.setSessionManager(sessionManager);

        oAuth2ResourceInterceptor.setAuthService(createMembraneAuthorizationService());
        oAuth2ResourceInterceptor.setLogoutUrl("/logout");
        oAuth2ResourceInterceptor.setSkipUserInfo(true);
        oAuth2ResourceInterceptor.setAppendAccessTokenToRequest(true);
        oAuth2ResourceInterceptor.setOnlyRefreshToken(true);
        oAuth2ResourceInterceptor.setLoginParameters(createLoginParameters());
        oAuth2ResourceInterceptor.setAfterLogoutUrl("/after-logout");

        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (!exc.getRequest().getUri().contains("is-logged-in"))
                    return Outcome.CONTINUE;

                boolean isLoggedIn = oAuth2ResourceInterceptor.getSessionManager().getSession(exc).isVerified();

                exc.setResponse(ok("{\"success\":" + isLoggedIn + "}").header(Header.CONTENT_TYPE, APPLICATION_JSON).build());
                return RETURN;
            }
        });
        sp.getInterceptors().add(oAuth2ResourceInterceptor);
        sp.getInterceptors().add(createTestResponseInterceptor());
        return sp;
    }

    private static @NotNull List<LoginParameter> createLoginParameters() {
        var withOutValue = new LoginParameter();
        withOutValue.setName("login_hint");

        var withValue = new LoginParameter();
        withValue.setName("foo");
        withValue.setValue("bar");

        return List.of(
                withOutValue,
                withValue
        );
    }

    private @NotNull MembraneAuthorizationService createMembraneAuthorizationService() {
        MembraneAuthorizationService auth = new MembraneAuthorizationService();
        auth.setSrc("http://localhost:" + MockAuthorizationServer.SERVER_PORT + "/" + tc.tenantId + "/" + tc.susiFlowId + "/v2.0");
        auth.setClientId(tc.clientId);
        auth.setClientSecret(tc.clientSecret);
        auth.setScope("openid profile offline_access");
        auth.setSubject("sub");
        return auth;
    }

    private ServiceProxy createFlowInitiatorServiceProxy(String path, String triggerFlow, boolean logoutBeforeFlow) {
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(tc.clientPort), null, 99999);
        Path p = new Path();
        p.setValue(path);
        sp.setPath(p);

        var flowInitiator = new FlowInitiator();

        flowInitiator.setDefaultFlow(tc.susiFlowId);
        flowInitiator.setTriggerFlow(triggerFlow);
        flowInitiator.setLogoutBeforeFlow(logoutBeforeFlow);
        flowInitiator.setLoginParameters(createLoginParameters2());
        flowInitiator.setAfterLoginUrl("/");
        flowInitiator.setOauth2(oAuth2Resource2Interceptor);

        sp.getInterceptors().add(flowInitiator);
        sp.getInterceptors().add(createTestResponseInterceptor());

        return sp;
    }

    private ServiceProxy createAfterLogoutServiceProxy() {
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(tc.clientPort), null, 99999);
        Path p = new Path();
        p.setValue("/after-logout");
        sp.setPath(p);

        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(ok().body("You have been logged out.").build());
                return RETURN;
            }
        });

        return sp;
    }

    private static @NotNull List<LoginParameter> createLoginParameters2() {
        var lp1 = new LoginParameter();
        lp1.setName("domain_hint");

        var lp2 = new LoginParameter();
        lp2.setName("fooflow");
        lp2.setValue("bar");

        return List.of(
                lp1, lp2
        );
    }

    @NotNull
    private AbstractInterceptor createTestResponseInterceptor() {
        return new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                try {
                    return handleRequestInternal(exc);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public Outcome handleRequestInternal(Exchange exc) throws IOException {
                OAuth2AnswerParameters answer = OAuth2AnswerParameters.deserialize(String.valueOf(exc.getProperty(Exchange.OAUTH2)));
                String accessToken = answer == null ? "null" : answer.getAccessToken();
                Map<String, String> body = new HashMap<>();
                if (accessToken != null)
                    body.put("accessToken", accessToken);
                body.put("path", exc.getRequestURI());
                body.put("method", exc.getRequest().getMethod());
                body.put("body", exc.getRequest().getBodyAsStringDecoded());

                exc.setResponse(ok(om.writeValueAsString(body)).build());
                return RETURN;
            }
        };
    }

    private ServiceProxy createRequireAuthServiceProxy(String expectedAudience, String path, Consumer<RequireAuth> requireAuthConfigurer) {
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(tc.clientPort), null, 99999);

        Path path2 = new Path();
        path2.setValue(path);
        sp.setPath(path2);

        var requireAuth = new RequireAuth();
        requireAuth.setExpectedAud(expectedAudience);
        requireAuth.setOauth2(oAuth2Resource2Interceptor);
        requireAuthConfigurer.accept(requireAuth);

        sp.getInterceptors().add(requireAuth);
        sp.getInterceptors().add(createTestResponseInterceptor());

        return sp;
    }

}
