package com.predic8.membrane.core.interceptor.acl2;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.acl2.address.IpAddress;
import com.predic8.membrane.core.interceptor.acl2.address.Ipv4Address;
import com.predic8.membrane.core.interceptor.acl2.rules.AccessRule;
import com.predic8.membrane.core.interceptor.acl2.targets.Hostname;
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

    // TODO keep this static?
    private static DNSCache dnsCache;
    private static boolean checkHostname = false;

    @Override
    public Outcome handleRequest(Exchange exc) {
        Optional<Boolean> permit = evaluatePermission(exc, rules);

        if (permit.isEmpty() || !permit.get()) {
            setResponseToAccessDenied(exc);
            return ABORT;
        }

        return CONTINUE;
    }

    @Override
    public void init(Router router, Proxy ignored) {
        super.init(router, ignored);
        dnsCache = router.getDnsCache();
        if (rules.stream().anyMatch(rule -> rule.getClass().isAssignableFrom(Hostname.class))) {
            checkHostname = true;
        }
    }

    static Optional<Boolean> evaluatePermission(Exchange exc, List<AccessRule> rules) {
        if (rules == null || rules.isEmpty())
            return Optional.empty();

        Optional<IpAddress> peerIp = parseIp(exc.getRemoteAddr());
        if (peerIp.isEmpty())
            return Optional.empty();

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

    public static Optional<IpAddress> parseIp(String raw) {
        Optional<Ipv4Address> v4 = Ipv4Address.parse(raw);
        if (v4.isPresent()) return Optional.of(v4.get());

        // TODO: implement Ipv6Address.parse(raw) analog
        // Optional<Ipv6Address> v6 = Ipv6Address.parse(raw);
        // if (v6.isPresent()) return Optional.of(v6.get());

        return Optional.empty();

    }

    @MCChildElement
    public void setRules(List<AccessRule> rules) {
        this.rules = rules;
    }

    public List<AccessRule> getRules() {
        return rules;
    }
}
