package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.exchange.Exchange;

public abstract class Target {

    protected final String address;

    protected Target(String address) {
        this.address = address;
    }

    public abstract boolean peerMatches(Exchange exc);

    public static Target byMatch(String address) {
        if (IpV4.accepts(address)) {
            return new IpV4(address);
        }

        if (IpV6.accepts(address)) {
            return new IpV6(address);
        }

        if (Hostname.accepts(address)) {
            return new Hostname(address);
        }

        throw new IllegalArgumentException("Address '" + address + "' is not compatible with any target type.");
    }

    @Override
    public String toString() {
        return address;
    }
}