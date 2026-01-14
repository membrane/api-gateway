package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.IpAddress;

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