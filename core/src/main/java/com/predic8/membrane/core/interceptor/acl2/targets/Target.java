package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.exchange.Exchange;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Target {

    private final String address;

    protected final Matcher matcher;

    public abstract boolean peerMatches(Exchange exc);

    public Target(String address) throws IncompatibleAddressException {
        this.address = address;
        this.matcher = getConstructionPattern().matcher(address);

        if (!matcher.matches()) {
            throw new IncompatibleAddressException();
        }
    }

    public abstract Pattern getConstructionPattern();

    public static Target byMatch(String address) {
        try {
            return new IpV4(address);
        } catch (IncompatibleAddressException ignored) {}

        try {
            return new IpV6(address);
        } catch (IncompatibleAddressException ignored) {}

        try {
            return new Hostname(address);
        } catch (IncompatibleAddressException ignored) {}

        throw new IllegalArgumentException("Address '" + address + "' is not compatible with any target type.");
    }

    @Override
    public String toString() {
        return address;
    }
}
