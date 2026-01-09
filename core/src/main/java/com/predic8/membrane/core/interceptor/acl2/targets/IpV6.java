package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.exchange.Exchange;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IpV6 extends Target {

    public IpV6(String address) throws IncompatibleAddressException {
        super(address);
        matcher.group("ip");
    }

    @Override
    public boolean peerMatches(Exchange exc) {
        return false;
    }

    @Override
    public Pattern getConstructionPattern() {
        return Pattern.compile(".*");
    }
}