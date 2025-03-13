package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.util.URIFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR;
import static com.predic8.membrane.core.util.URLParamUtil.getParams;

public class OAuth2Parameters {
    private static final Logger log = LoggerFactory.getLogger(OAuth2Parameters.class);

    private final Map<String, String> params;

    public OAuth2Parameters(URIFactory uriFactory, Exchange exc) throws URISyntaxException, IOException {
        params = getParams(uriFactory, exc, ERROR);
    }

    public static OAuth2Parameters parse(URIFactory uriFactory, Exchange exc) throws URISyntaxException, IOException {
        return new OAuth2Parameters(uriFactory, exc);
    }

    public String getState() {
        return params.get("state");
    }

    public String getCode() {
        return params.get("code");
    }


    public void checkCodeOrError() throws OAuth2Exception {
        if (getCode() != null)
            return;
        String error = getError();
        if (error == null)
            throw new RuntimeException("No code received.");
        log.warn("OAuth2 Error from Authentication Server: {}", error);
        ProblemDetails pd = security(false,"oauth2-callback-request-handler")
                .title("OAuth2 Error from Authentication Server")
                .statusCode(500)
                .addSubSee("oauth2-error-from-authentication-server")
                .detail(getErrorDescription())
                .internal("error", error);
        throw new OAuth2Exception(error, getErrorDescription(), pd.build());
    }

    private String getErrorDescription() {
        return params.get("error_description");
    }

    private String getError() {
        return params.get("error");
    }
}
