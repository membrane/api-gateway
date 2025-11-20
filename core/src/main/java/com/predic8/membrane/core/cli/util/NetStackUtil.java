package com.predic8.membrane.core.cli.util;

import org.slf4j.Logger;

import java.net.*;
import java.util.Enumeration;

import static com.predic8.membrane.core.cli.util.NetStackUtil.IpStack.*;
import static com.predic8.membrane.core.cli.util.NetStackUtil.IpStack.NONE;

public final class NetStackUtil {

    public enum IpStack {
        NONE,
        IPV4_ONLY,
        IPV6_ONLY,
        IPV6_LINK_LOCAL_ONLY,
        DUAL_STACK
    }

    public static void logIfStackLimited(Logger log) {
        try {
            switch (detectIpStack()) {
                case IPV4_ONLY ->
                        log.warn("Only IPv4 available. No global IPv6 addresses detected.");
                case IPV6_ONLY ->
                        log.warn("Only IPv6 available. No IPv4 addresses detected.");
                case IPV6_LINK_LOCAL_ONLY ->
                        log.warn("Only IPv6 link-local addresses detected. IPv6 not globally usable.");
                case NONE ->
                        log.warn("No valid IP stack detected.");
                default -> { /* DUAL_STACK → no log */ }
            }
        } catch (Exception e) {
            log.warn("Failed to detect IP stack.", e);
        }
    }

    private static IpStack detectIpStack() throws SocketException {
        boolean hasIPv4 = false;
        boolean hasIPv6Global = false;
        boolean hasIPv6LinkLocal = false;

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                continue;
            }

            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress addr = ia.getAddress();
                switch (addr) {
                    case null -> {
                        continue;
                    }
                    case Inet4Address ignored -> hasIPv4 = true;
                    case Inet6Address ignored -> {
                        if (addr.isLinkLocalAddress()) {
                            hasIPv6LinkLocal = true;
                        } else {
                            hasIPv6Global = true;
                        }
                    }
                    default -> {
                    }
                }

                if (hasIPv4 && hasIPv6Global) {
                    return DUAL_STACK;
                }
            }
        }

        if (hasIPv4) return IPV4_ONLY;
        if (hasIPv6Global) return IPV6_ONLY;
        if (hasIPv6LinkLocal) return IPV6_LINK_LOCAL_ONLY;
        return NONE;
    }

}

