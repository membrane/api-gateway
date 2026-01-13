package com.predic8.membrane.core.interceptor.acl2.address;

import java.net.InetAddress;

public final class Ipv6Address implements IpAddress {

    String hostname = "";

    @Override
    public IpVersion version() {
        return null;
    }

    @Override
    public InetAddress getAddress() {
        return null;
    } /* ... */

    @Override
    public String getHostname() {
        return "";
    }

    @Override
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
}
