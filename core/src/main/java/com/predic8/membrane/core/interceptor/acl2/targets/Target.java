package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.IpAddress;

/**
 * Waht is a Target?
 */
public abstract class Target {

    protected final String address;

    protected Target(String address) {
        this.address = address;
    }

    // TODO add and rewrite tests

    public abstract boolean peerMatches(IpAddress address);

    public static Target byMatch(String address) {

        // Combine both
        if (Ipv4Target.accepts(address)) {
            return new Ipv4Target(address);
        }

        if (Ipv6Target.accepts(address)) {
            return new Ipv6Target(address);
        }

        if (HostnameTarget.accepts(address)) {
            return new HostnameTarget(address);
        }

        throw new IllegalArgumentException("Address '" + address + "' is not compatible with any target type.");
    }

    @Override
    public String toString() {
        return address;
    }
}