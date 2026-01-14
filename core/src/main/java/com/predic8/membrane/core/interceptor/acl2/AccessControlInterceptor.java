package com.predic8.membrane.core.interceptor.acl2;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.acl2.address.*;
import com.predic8.membrane.core.interceptor.acl2.rules.*;
import com.predic8.membrane.core.interceptor.acl2.targets.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.util.*;

import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

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

        Optional<IpAddress> peerIp = parseIp(exc.getRemoteAddr());
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
    public static Optional<IpAddress> parseIp(String raw) {
        Optional<Ipv4Address> v4 = Ipv4Address.parse(raw);
        if (v4.isPresent()) return Optional.of(v4.get());

        Optional<Ipv6Address> v6 = Ipv6Address.parse(raw);
        if (v6.isPresent()) return Optional.of(v6.get());

        return Optional.empty();

    }

    @MCChildElement
    public void setRules(List<AccessRule> rules) {
        this.rules = rules;
    }

    public List<AccessRule> getRules() {
        return rules;
    }

    private boolean hasHostnameRule() {
        return rules.stream().anyMatch(rule -> rule.getClass().isAssignableFrom(Hostname.class));
    }
}
