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

    public static Target byMatch(String addr) {
        return Ipv4Target.tryCreate(addr)
                .or(() -> Ipv6Target.tryCreate(addr))
                .or(() -> HostnameTarget.tryCreate(addr))
                .orElseThrow(() -> new IllegalArgumentException("Address '" + addr + "' is not compatible with any target type."));
    }


    @Override
    public String toString() {
        return address;
    }
}