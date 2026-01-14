package com.predic8.membrane.core.interceptor.acl2;

import com.predic8.membrane.core.interceptor.acl2.rules.AccessRule;
import com.predic8.membrane.core.interceptor.acl2.targets.HostnameTarget;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;

import java.util.List;
import java.util.Optional;

public class AccessControl {

    private List<AccessRule> rules;

    private PeerAddressResolver peerAddressResolver;

    public void init(Router router) {
        peerAddressResolver = new PeerAddressResolver(hasHostnameRule(), router.getDnsCache());
        if (rules.isEmpty()) throw new ConfigurationException("No access rules defined.");
    }

    public boolean isPermitted(String remoteIp) {
        return peerAddressResolver.resolve(remoteIp)
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

    public boolean hasHostnameRule() {
        return rules.stream().anyMatch(rule -> rule.getClass().isAssignableFrom(HostnameTarget.class));
    }

    public List<AccessRule> getRules() {
        return rules;
    }

    public void setRules(List<AccessRule> rules) {
        this.rules = rules;
    }

}
