package com.predic8.membrane.core.interceptor.acl.matchers;

import com.predic8.membrane.core.interceptor.acl.TypeMatcher;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class CidrMatcher implements TypeMatcher {
    @Override
    public boolean matches(String value, String schema) {
        try {

            // @Todo CidrRange cr = CidrRange.parse(schema)
            String[] parts = schema.split("/");
            InetAddress cidrAddress = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            byte[] cidrAddressBytes = cidrAddress.getAddress();
            byte[] currentAddressBytes = InetAddress.getByName(value).getAddress();

            if (cidrAddressBytes.length != 4 || currentAddressBytes.length != 4) {
                throw new IllegalArgumentException("Only IPv4 addresses are supported");
            }


            int mask = getMask(prefixLength);

            int networkAddress = getIntegerValue(cidrAddressBytes) & mask;
            int broadcastAddress = networkAddress | ~mask;

            // End Todo

            int currentAddressInt = getIntegerValue(currentAddressBytes);
            return (currentAddressInt >= networkAddress && currentAddressInt <= broadcastAddress);
        } catch (UnknownHostException | NumberFormatException e) {
            e.printStackTrace();
            return false;
        }
    }

    // @Todo Test
    private static int getMask(int prefixLength) {
        return ~0 << (32 - prefixLength);
    }

    // @Todo Test
    private static int getIntegerValue(byte[] cidrAddressBytes) {
        return ByteBuffer.wrap(cidrAddressBytes).getInt();
    }
}
