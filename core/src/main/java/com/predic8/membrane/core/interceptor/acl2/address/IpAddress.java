package com.predic8.membrane.core.interceptor.acl2.address;

import java.net.InetAddress;

public abstract class IpAddress {

    private String hostname = "";

    public abstract ipVersion version();

    public abstract InetAddress getAddress();

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public enum ipVersion {
        IPV4, IPV6
    }
}
