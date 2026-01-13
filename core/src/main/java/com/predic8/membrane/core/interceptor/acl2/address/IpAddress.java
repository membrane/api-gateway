package com.predic8.membrane.core.interceptor.acl2.address;

import java.net.InetAddress;

import static com.predic8.membrane.core.interceptor.acl2.address.IpAddress.IpVersion.IPV4;
import static com.predic8.membrane.core.interceptor.acl2.address.IpAddress.IpVersion.IPV6;

public sealed interface IpAddress permits Ipv4Address, Ipv6Address {

    void setHostname(String hostName);

    String getHostname();

    IpVersion version();
    InetAddress getAddress();

    default boolean isV4() { return version() == IPV4; }
    default boolean isV6() { return version() == IPV6; }

    enum IpVersion {
        IPV4, IPV6
    }
}
