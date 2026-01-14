package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.IpAddress;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HostnameTarget extends Target {

    private static final Pattern HOSTNAME_PATTERN = Pattern.compile("^.*$");

    private final Pattern hostname;

    public static boolean accepts(String address) {
        return HOSTNAME_PATTERN.matcher(address).matches();
    }

    public HostnameTarget(String hostname) {
        super(hostname);

        // TODO validate configured name/pattern
        Matcher matcher = HOSTNAME_PATTERN.matcher(hostname);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid hostname format: " + hostname);
        }

        this.hostname = Pattern.compile(matcher.group("hostname"));
    }

    @Override
    public boolean peerMatches(IpAddress address) {
        return hostname.matcher(address.getHostname()).matches();
    }
}
