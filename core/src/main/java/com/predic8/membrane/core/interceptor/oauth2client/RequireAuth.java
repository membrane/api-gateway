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
package com.predic8.membrane.core.interceptor.oauth2client;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.jwt.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor.*;

/**
 * @description Requires a valid access token for the request, accepted either as a Bearer JWT
 * (validated directly against the configured <code>oauth2</code> client's JWKS) or, when no bearer
 * token is present, via that client's normal session/login flow. After a successful session flow the
 * request still passes through JWT validation before continuing.
 */
@MCElement(name = "requireAuth")
public class RequireAuth extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequireAuth.class.getName());

    private String expectedAud;
    private String expectedTid;
    private OAuth2Resource2Interceptor oauth2;
    private JwtAuthInterceptor jwtAuth;
    private boolean required = true;
    private Integer errorStatus = null;
    private String scope = null;

    @Override
    public void init() {
        super.init();
        var jwks = new Jwks();
        jwks.setJwks(new ArrayList<>());
        // TODO init dependency
        try {
            jwks.setJwksUris(oauth2.getAuthService().getJwksEndpoint());
        } catch (Exception e) {
            throw new ConfigurationException("Could not set jwks Uris.",e);
        }
        jwks.setAuthorizationService(oauth2.getAuthService());
        jwtAuth = new JwtAuthInterceptor();
        jwtAuth.setJwks(jwks);
        jwtAuth.setExpectedAud(expectedAud);
        jwtAuth.setExpectedTid(expectedTid);

        jwtAuth.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (!isBearer(exc.getRequest().getHeader())) {
            if (errorStatus != null)
                exc.setProperty(ERROR_STATUS, errorStatus);
            exc.setProperty(EXPECTED_AUDIENCE, expectedAud);
            exc.setProperty(EXPECTED_TENANT_ID, expectedTid);
            exc.setProperty(WANTED_SCOPE, scope);
            var outcome = oauth2.handleRequest(exc);
            if (outcome != Outcome.CONTINUE) {
                if (!required)
                    return Outcome.CONTINUE;
                return outcome;
            }
        }

        return jwtAuth.handleRequest(exc);
    }

    private boolean isBearer(Header header) {
        return header.contains(AUTHORIZATION)
                && header.getFirstValue(AUTHORIZATION).startsWith("Bearer");
    }

    public String getExpectedAud() {
        return expectedAud;
    }

    public String getExpectedTid() {
        return expectedTid;
    }

    /**
     * @description Expected <code>aud</code> (audience) claim value that a presented JWT must contain.
     */
    @Required
    @MCAttribute
    public void setExpectedAud(String expectedAud) {
        this.expectedAud = expectedAud;
        if (jwtAuth != null) {
            jwtAuth.setExpectedAud(expectedAud);
        }
    }

    /**
     * @description Expected tenant ID claim value that a presented JWT must contain, when the
     * identity provider issues tokens per tenant.
     */
    @MCAttribute
    public void setExpectedTid(String expectedTid) {
        this.expectedTid = expectedTid;
        if (jwtAuth != null) {
            jwtAuth.setExpectedTid(expectedTid);
        }
    }

    public OAuth2Resource2Interceptor getOauth2() {
        return oauth2;
    }

    /**
     * @description The <code>oauth2</code> client interceptor supplying the JWKS and the
     * session/login flow used when no bearer token is present.
     */
    @Required
    @MCAttribute
    public void setOauth2(OAuth2Resource2Interceptor oauth2) {
        this.oauth2 = oauth2;
    }

    /**
     * @description Whether authentication is enforced. When <code>false</code>, a request that
     * fails the oauth2 session/redirect flow is allowed to continue anyway.
     * @default true
     */
    @SuppressWarnings("SameParameterValue")
    @MCAttribute
    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isRequired() {
        return required;
    }

    public Integer getErrorStatus() {
        return errorStatus;
    }

    /**
     * @description HTTP status code to use instead of the oauth2 flow's own status when
     * authentication fails.
     */
    @MCAttribute
    public void setErrorStatus(int errorStatus) {
        this.errorStatus = errorStatus;
    }

    public String getScope() {
        return scope;
    }

    /**
     * @description OAuth2 scope that must be granted for the request to be authorized.
     */
    @MCAttribute
    public void setScope(String scope) {
        this.scope = scope;
    }
}
