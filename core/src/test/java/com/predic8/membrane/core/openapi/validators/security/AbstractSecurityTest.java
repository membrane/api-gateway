package com.predic8.membrane.core.openapi.validators.security;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.security.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;

public abstract class AbstractSecurityTest {

    @NotNull
    Exchange getExchange(String path, SecurityScheme scheme) throws URISyntaxException {
        return getExchange("GET", path, scheme);
    }

    @NotNull
    Exchange getExchange(String method, String path, SecurityScheme scheme) throws URISyntaxException {
        Exchange exc = new Request.Builder().method(method).url(new URIFactory(),path).buildExchange();
        exc.setOriginalRequestUri(path);
        if (scheme!=null)
            exc.setProperty(SECURITY_SCHEMES, List.of(scheme));
        return exc;
    }

    void dumpResponseBody(Exchange exc) {
        if (exc.getResponse() != null)
            System.out.println("Body:\n" + exc.getResponse().getBodyAsStringDecoded());
    }

    static Router getRouter() {
        Router router = new Router();
        router.setUriFactory(new URIFactory());
        return router;
    }
}
