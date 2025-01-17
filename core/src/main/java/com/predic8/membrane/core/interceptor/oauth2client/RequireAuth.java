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

@MCElement(name = "requireAuth")
public class RequireAuth extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequireAuth.class.getName());

    private String expectedAud;
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

        jwtAuth.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (!isBearer(exc.getRequest().getHeader())) {
            if (errorStatus != null)
                exc.setProperty(ERROR_STATUS, errorStatus);
            exc.setProperty(EXPECTED_AUDIENCE, expectedAud);
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

    @Required
    @MCAttribute
    public void setExpectedAud(String expectedAud) {
        this.expectedAud = expectedAud;
        if (jwtAuth != null) {
            jwtAuth.setExpectedAud(expectedAud);
        }
    }

    public OAuth2Resource2Interceptor getOauth2() {
        return oauth2;
    }

    @Required
    @MCAttribute
    public void setOauth2(OAuth2Resource2Interceptor oauth2) {
        this.oauth2 = oauth2;
    }

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

    @MCAttribute
    public void setErrorStatus(int errorStatus) {
        this.errorStatus = errorStatus;
    }

    public String getScope() {
        return scope;
    }

    @MCAttribute
    public void setScope(String scope) {
        this.scope = scope;
    }
}
