package com.predic8.membrane.core.interceptor.oauth2client;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.jwt.Jwks;
import com.predic8.membrane.core.interceptor.jwt.JwtAuthInterceptor;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor.ERROR_STATUS;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor.EXPECTED_AUDIENCE;

@MCElement(name = "requireAuth")
public class RequireAuth extends AbstractInterceptor {

    private String expectedAud;
    private OAuth2Resource2Interceptor oauth2;
    private JwtAuthInterceptor jwtAuth;
    private boolean required = true;
    private Integer errorStatus = null;
    private String scope = null;

    @Override
    public void init(Router router) throws Exception {
        super.init(router);

        var jwks = new Jwks();
        jwks.setJwks(new ArrayList<>());
        // TODO init dependency
        jwks.setJwksUris(oauth2.getAuthService().getJwksEndpoint());
        jwks.setAuthorizationService(oauth2.getAuthService());


        jwtAuth = new JwtAuthInterceptor();
        jwtAuth.setJwks(jwks);
        jwtAuth.setExpectedAud(expectedAud);

        jwtAuth.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (!isBearer(exc.getRequest().getHeader())) {
            if (errorStatus != null)
                exc.setProperty(ERROR_STATUS, errorStatus);
            exc.setProperty(EXPECTED_AUDIENCE, expectedAud);
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
    public void setErrorStatus(Integer errorStatus) {
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
