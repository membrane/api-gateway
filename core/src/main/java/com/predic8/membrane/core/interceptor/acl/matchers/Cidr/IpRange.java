package com.predic8.membrane.core.interceptor.acl.matchers.Cidr;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class IpRange {
    private final int networkAddress;
    private final int broadcastAddress;

    private IpRange(int networkAddress, int broadcastAddress) {
        this.networkAddress = networkAddress;
        this.broadcastAddress = broadcastAddress;
    }

    public static IpRange fromCidr(String schema) throws UnknownHostException {
        String[] parts = schema.split("/");
        InetAddress cidrAddress = InetAddress.getByName(parts[0]);
        int prefixLength = Integer.parseInt(parts[1]);

        if (cidrAddress.getAddress().length != 4) {
            throw new IllegalArgumentException("Only IPv4 addresses are supported");
        }

        int mask = getMask(prefixLength);
        int networkAddress = getIntegerValue(cidrAddress.getAddress()) & mask;
        int broadcastAddress = networkAddress | ~mask;

        return new IpRange(networkAddress, broadcastAddress);
    }

    public boolean contains(String ipAddress) throws UnknownHostException {
        byte[] addressBytes = InetAddress.getByName(ipAddress).getAddress();
        int addressInt = getIntegerValue(addressBytes);
        return addressInt >= this.networkAddress && addressInt <= this.broadcastAddress;
    }

    static int getMask(int prefixLength) {
        return prefixLength == 0 ? 0 : ~0 << (32 - prefixLength);
    }

    static int getIntegerValue(byte[] addressBytes) {
        return ByteBuffer.wrap(addressBytes).getInt();
    }
}
