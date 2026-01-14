package com.predic8.membrane.core.interceptor.acl2;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.acl2.rules.AccessRule;
import com.predic8.membrane.core.interceptor.acl2.targets.HostnameTarget;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;

import java.util.List;
import java.util.Optional;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "accessControl")
public class AccessControlInterceptor extends AbstractInterceptor {

    private List<AccessRule> rules;

    private PeerAddressResolver peerAddressResolver;

    @Override
    public void init(Router router, Proxy proxy) {
        super.init(router, proxy);
        peerAddressResolver = new PeerAddressResolver(hasHostnameRule(), router.getDnsCache());
        if (rules.isEmpty()) throw new ConfigurationException("No access rules defined.");
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (!isPermitted(exc)) {
            setResponseToAccessDenied(exc);
            return ABORT;
        }
        return CONTINUE;
    }

    private boolean isPermitted(Exchange exc) {
        return peerAddressResolver.resolve(exc.getRemoteAddrIp())
                .map(this::evaluatePermission)
                .orElse(false);
    }

    private boolean evaluatePermission(IpAddress address) {
        for (AccessRule rule : rules) {
            Optional<Boolean> res = rule.apply(address);
            if (res.isPresent()) return res.get();
        }
        return false;
    }

    // Copied from old ACL as is
    private void setResponseToAccessDenied(Exchange exc) {
        security(false, getDisplayName())
                .title("Access Denied")
                .status(401)
                .addSubSee("authorization-denied")
                .buildAndSetResponse(exc);
    }

    private boolean hasHostnameRule() {
        return rules.stream().anyMatch(rule -> rule.getClass().isAssignableFrom(HostnameTarget.class));
    }

    @MCChildElement
    public void setRules(List<AccessRule> rules) {
        this.rules = rules;
    }

    public List<AccessRule> getRules() {
        return rules;
    }
}
