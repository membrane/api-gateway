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
package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.interceptor.log.LogInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.BearerTokenGenerator;
import com.predic8.membrane.core.proxies.SSLableProxy;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.test.OAuth2AuthFlowClient;
import com.predic8.membrane.test.OAuth2AuthFlowFormPostClient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.predic8.membrane.core.interceptor.log.LogInterceptor.Level.DEBUG;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;

public class OAuth2RedirectFormPostTest extends OAuth2RedirectTest {

    OAuth2AuthFlowClient getOAuth2AuthFlowClient() {
        return new OAuth2AuthFlowFormPostClient(AUTH_SERVER_BASE_URL, CLIENT_BASE_URL);
    }

    @NotNull SSLableProxy getAuthorizationServerRule() {
        SSLableProxy azureRule = new ServiceProxy(new ServiceProxyKey(AUTH_SERVER_BASE_URL.getHost(), "*", ".*", AUTH_SERVER_BASE_URL.getPort()), "localhost", 80);
        azureRule.getFlow().add(new LogInterceptor() {{
            setLevel(DEBUG);
        }});
        azureRule.getFlow().add(new OAuth2AuthorizationServerInterceptor() {
            {
                setLocation(getPathFromResource("openId/dialog"));
                setConsentFile(getPathFromResource("openId/consentFile.json"));
                setTokenGenerator(new BearerTokenGenerator());
                setIssuer(AUTH_SERVER_BASE_URL.toString());
                setUserDataProvider(new StaticUserDataProvider() {{
                    setUsers(List.of(new User() {{
                        setUsername("user");
                        setPassword("password");
                    }}));
                }});
                setClientList(new StaticClientList() {{
                    setClients(List.of(new Client() {{
                        setClientId("abc");
                        setClientSecret("def");
                        setCallbackUrl(CLIENT_BASE_URL.resolve("/oauth2callback").toString());
                    }}));
                }});
                setClaimList(new ClaimList() {{
                    setValue("aud email iss sub username");
                    setScopes(new ArrayList<>() {{
                        add(new Scope() {{
                            setId("username");
                            setClaims("username");
                        }});
                        add(new Scope() {{
                            setId("profile");
                            setClaims("username email");
                        }});
                    }});
                }});
            }

            @Override
            public void init() {
                super.init();
                getWellknownFile().setSupportedResponseModes(Set.of("query", "fragment", "form_post"));
            }
        });
        return azureRule;
    }
}
