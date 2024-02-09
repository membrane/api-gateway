package com.predic8.membrane.core.interceptor.oauth2client;

import com.predic8.membrane.annot.MCAttribute;
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

import static com.predic8.membrane.core.http.Header.*;

@MCElement(name = "requireAuth")
public class RequireAuth extends AbstractInterceptor {

    private String expectedAud;
    private OAuth2Resource2Interceptor oauth2;

    private JwtAuthInterceptor jwtAuth;

    @Override
    public void init(Router router) throws Exception {
        super.init(router);

        var jwks = new Jwks();
        jwks.setJwks(new ArrayList<>());
        jwks.setJwksUris(oauth2.getAuthService().getJwksEndpoint());

        jwtAuth = new JwtAuthInterceptor();
        jwtAuth.setJwks(jwks);
        jwtAuth.setExpectedAud(expectedAud);

        jwtAuth.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (!isBearer(exc.getRequest().getHeader())) {
            var outcome = oauth2.handleRequest(exc);
            if (outcome != Outcome.CONTINUE) {
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
    }

    public OAuth2Resource2Interceptor getOauth2() {
        return oauth2;
    }

    @Required
    @MCAttribute
    public void setOauth2(OAuth2Resource2Interceptor oauth2) {
        this.oauth2 = oauth2;
    }
}
