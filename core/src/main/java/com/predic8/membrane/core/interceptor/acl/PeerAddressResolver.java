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

import com.predic8.membrane.core.util.DNSCache;

import java.util.Optional;

import static com.predic8.membrane.core.interceptor.acl.IpAddress.parse;

public final class PeerAddressResolver {

    private final boolean checkHostname;
    private final DNSCache dnsCache;

    public PeerAddressResolver(boolean checkHostname, DNSCache dnsCache) {
        this.checkHostname = checkHostname;
        this.dnsCache = dnsCache;
    }

    public Optional<IpAddress> resolve(String rawRemoteIp) {
        if (rawRemoteIp == null) return Optional.empty();
        String s = rawRemoteIp.trim();
        if (s.isEmpty()) return Optional.empty();

        IpAddress ip;
        try {
            ip = parse(s);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        if (checkHostname) {
            ip.setHostname(dnsCache.getCanonicalHostName(ip.getInetAddress()));
        }

        return Optional.of(ip);
    }
}
