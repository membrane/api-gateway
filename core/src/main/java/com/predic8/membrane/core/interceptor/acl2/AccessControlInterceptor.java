package com.predic8.membrane.core.interceptor.acl2;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.acl2.rules.AccessRule;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.router.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "accessControl")
public class AccessControlInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AccessControlInterceptor.class);

    private final AccessControl accessControl = new AccessControl();

    @Override
    public void init(Router router, Proxy proxy) {
        super.init(router, proxy);
        accessControl.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        String remoteIp = exc.getRemoteAddrIp();
        if (!accessControl.isPermitted(remoteIp)) {
            setResponseToAccessDenied(exc);
            log.debug("Access denied. remoteIp={} method={} uri={}", remoteIp, exc.getRequest().getMethod(), exc.getRequestURI());
            return ABORT;
        }
        return CONTINUE;
    }

    // Copied from old ACL as is
    private void setResponseToAccessDenied(Exchange exc) {
        security(false, getDisplayName())
                .title("Access Denied")
                .status(401)
                .addSubSee("authorization-denied")
                .buildAndSetResponse(exc);
    }

    @MCChildElement
    public void setRules(List<AccessRule> rules) {
        accessControl.setRules(rules);
    }

    public List<AccessRule> getRules() {
        return accessControl.getRules();
    }
}
