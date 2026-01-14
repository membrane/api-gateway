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
import com.predic8.membrane.core.util.DNSCache;

import java.util.List;
import java.util.Optional;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "accessControl")
public class AccessControlInterceptor extends AbstractInterceptor {

    private List<AccessRule> rules;

    // TODO keep this static? Nop
    private DNSCache dnsCache;
    private boolean checkHostname = false;

    @Override
    public void init(Router router, Proxy proxy) {
        super.init(router, proxy);
        dnsCache = router.getDnsCache();
        checkHostname = hasHostnameRule();
        // No accessControl Rules => error
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        // log.debug
        Optional<Boolean> permit = evaluatePermission(exc, rules);

        if (permit.isEmpty() || !permit.get()) {
            setResponseToAccessDenied(exc);
            return ABORT;
        }

        return CONTINUE;
    }

    /**
     * Define return
     * Idea: Simpler return boolean
     * @param exc
     * @param rules
     * @return
     */
    Optional<Boolean> evaluatePermission(Exchange exc, List<AccessRule> rules) {
        if (rules == null || rules.isEmpty())
            return Optional.empty(); // Log no accessRules, move to init

        Optional<IpAddress> peerIp = parseIp(exc.getRemoteAddrIp());
        if (peerIp.isEmpty())
            return Optional.empty();

        // => move to IpAddress, Factory?
        IpAddress address = peerIp.get();
        if (checkHostname) {
            address.setHostname(dnsCache.getCanonicalHostName(address.getAddress()));
        }

        for (AccessRule rule : rules) {
            Optional<Boolean> res = rule.apply(address);
            if (res.isPresent()) return res;
        }

        return Optional.empty();
    }

    // Copied from old ACL as is
    private void setResponseToAccessDenied(Exchange exc) {
        security(false, getDisplayName())
                .title("Access Denied")
                .status(401)
                .addSubSee("authorization-denied")
                .buildAndSetResponse(exc);
    }

    // => Util
    /**
     * Incoming remote address is expected to be a valid IPv4/IPv6 literal.
     * Returns empty only if input is null/blank or parsing unexpectedly fails.
     */
    public static Optional<IpAddress> parseIp(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.trim();
        if (s.isEmpty()) return Optional.empty();

        try {
            return Optional.of(IpAddress.parse(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }


    @MCChildElement
    public void setRules(List<AccessRule> rules) {
        this.rules = rules;
    }

    public List<AccessRule> getRules() {
        return rules;
    }

    private boolean hasHostnameRule() {
        return rules.stream().anyMatch(rule -> rule.getClass().isAssignableFrom(HostnameTarget.class));
    }
}
