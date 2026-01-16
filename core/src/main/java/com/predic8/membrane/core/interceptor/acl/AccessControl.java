/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.acl;

import com.predic8.membrane.core.interceptor.acl.rules.AccessRule;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.DNSCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates ACL access rules for an incoming peer.
 *
 * <p>{@link AccessControl} resolves the remote peer into an {@link IpAddress} (optionally including a hostname via DNS,
 * if any rule depends on hostname matching) and then applies the configured {@link AccessRule}s in order.</p>
 *
 * <p>Rule evaluation is "first decision wins":</p>
 * <ul>
 *   <li>If a rule matches the peer, it returns a permit/deny decision.</li>
 *   <li>If a rule does not match, it returns {@link Optional#empty()} and evaluation continues.</li>
 *   <li>If no rule makes a decision, access is denied by default.</li>
 * </ul>
 */
public class AccessControl {

    private List<AccessRule> rules = new ArrayList<>();

    private PeerAddressResolver peerAddressResolver;

    public void init(DNSCache dnsCache) {
        peerAddressResolver = new PeerAddressResolver(hasHostnameRule(), dnsCache);
        if (rules.isEmpty()) throw new ConfigurationException("No access rules defined.");
    }

    /**
     * Evaluates whether a remote peer is permitted.
     *
     * <p>If the remote IP cannot be resolved to a valid IP literal (null/blank/unexpected parse failure),
     * access is denied.</p>
     *
     * @param remoteIp remote peer IP as string (expected to be a valid IPv4/IPv6 literal)
     * @return true if permitted by the first matching rule, otherwise false
     */
    public boolean isPermitted(String remoteIp) {
        return peerAddressResolver.resolve(remoteIp)
                .map(this::evaluatePermission)
                .orElse(false);
    }

    /**
     * Applies the configured rules in order and returns the first decision.
     * If no rule decides, returns false (default deny).
     */
    private boolean evaluatePermission(IpAddress address) {
        for (AccessRule rule : rules) {
            Optional<Boolean> res = rule.apply(address);
            if (res.isPresent()) return res.get();
        }
        return false;
    }

    public boolean hasHostnameRule() {
        return rules.stream().anyMatch(AccessRule::isHostnameRule);
    }

    public List<AccessRule> getRules() {
        return rules;
    }

    public void setRules(List<AccessRule> rules) {
        this.rules = rules;
    }

}
