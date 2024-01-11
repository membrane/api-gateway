package com.predic8.membrane.core.interceptor.acl.matchers.Cidr;

import com.predic8.membrane.core.interceptor.acl.TypeMatcher;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class CidrMatcher implements TypeMatcher {
    @Override
    public boolean matches(String value, String schema) {
        try {
            IpRange cr = IpRange.fromCidr(schema);
            return cr.contains(value);
        } catch (UnknownHostException | IllegalArgumentException e) {
            e.printStackTrace();
            return false;
        }
    }
}
