package com.predic8.membrane.core.interceptor.acl2.targets;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class TargetUtil {

    public static boolean isIpInCidr(Inet4Address address, int cidr, Inet4Address target) {
        int baseIpInt = ipToInt(address);
        int targetIpInt = ipToInt(target);

        int mask = (cidr == 0) ? 0 : (-1) << (32 - cidr);

        return (baseIpInt & mask) == (targetIpInt & mask);
    }

    static int ipToInt(InetAddress ip) {
        return ByteBuffer.wrap(ip.getAddress()).getInt();
    }
}
