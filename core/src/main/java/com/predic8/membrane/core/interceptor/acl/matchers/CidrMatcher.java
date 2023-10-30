package com.predic8.membrane.core.interceptor.acl.matchers;

import com.predic8.membrane.core.interceptor.acl.TypeMatcher;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class CidrMatcher implements TypeMatcher {
    @Override
    public boolean matches(String value, String schema) {
        try {
            String[] parts = schema.split("/");
            InetAddress cidrAddress = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);

            byte[] cidrAddressBytes = cidrAddress.getAddress();
            byte[] currentAddressBytes = InetAddress.getByName(value).getAddress();

            if (cidrAddressBytes.length != 4 || currentAddressBytes.length != 4) {
                throw new IllegalArgumentException("Only IPv4 addresses are supported");
            }

            int cidrAddressInt = ByteBuffer.wrap(cidrAddressBytes).getInt();
            int currentAddressInt = ByteBuffer.wrap(currentAddressBytes).getInt();

            int mask = ~0 << (32 - prefixLength);

            int networkAddress = cidrAddressInt & mask;
            int broadcastAddress = networkAddress | ~mask;

            return (currentAddressInt >= networkAddress && currentAddressInt <= broadcastAddress);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return false;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return false;
        }
    }
}
