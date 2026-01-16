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

package com.predic8.membrane.core.interceptor.acl.targets;

import com.predic8.membrane.core.interceptor.acl.IpAddress;

/**
 * Base type for configured peer selectors used by ACL rules.
 *
 * <p>A {@link Target} is created from a configuration string (e.g. IPv4/IPv6 with optional CIDR, or a hostname regex)
 * and can be matched against an incoming peer {@link IpAddress}.</p>
 *
 * <p>{@link #byMatch(String)} tries to create a concrete target type in this order:</p>
 * <ol>
 *   <li>{@link Ipv4Target}</li>
 *   <li>{@link Ipv6Target}</li>
 *   <li>{@link HostnameTarget}</li>
 * </ol>
 */
public abstract class Target {

    protected final String address;

    protected Target(String address) {
        this.address = address;
    }

    /**
     * Creates a {@link Target} from a configuration string by trying all known target types.
     *
     * @param addr configuration value
     * @return the first matching target type
     * @throws IllegalArgumentException if the value is not compatible with any target type
     */
    public static Target byMatch(String addr) {
        return Ipv4Target.tryCreate(addr)
                .or(() -> Ipv6Target.tryCreate(addr))
                .or(() -> HostnameTarget.tryCreate(addr))
                .orElseThrow(() -> new IllegalArgumentException("Address '" + addr + "' is not compatible with any target type."));
    }

    /**
     * @param address peer address to validate
     * @return true if this target matches the peer, otherwise false
     */
    public abstract boolean peerMatches(IpAddress address);

    @Override
    public String toString() {
        return address;
    }
}