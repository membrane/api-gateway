package com.predic8.membrane.core.interceptor.acl.targets;

import com.predic8.membrane.core.interceptor.acl.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.acl.IpAddress.ipVersion.IPV4;
import static com.predic8.membrane.core.util.NetworkUtil.*;
import static java.lang.Integer.parseInt;
import static java.util.Optional.empty;
import static java.util.Optional.of;

/**
 * IPv4 target definition with optional CIDR prefix (e.g. {@code 192.168.1.0/24}).
 *
 * <p>This class is used to validate incoming {@link IpAddress} instances (peers) against a configured IPv4
 * network/prefix. The configured input is expected to be an IPv4 literal (strict dotted-quad) optionally
 * followed by {@code /<cidr>}.</p>
 *
 * <p>Matching is done via bit masking:</p>
 * <ul>
 *   <li>{@code mask} is derived from {@code cidr} and has the top {@code cidr} bits set to 1.</li>
 *   <li>{@code network} is the masked base address: {@code network = targetIp & mask}.</li>
 *   <li>A peer matches iff {@code (peerIp & mask) == network}.</li>
 * </ul>
 */
public final class Ipv4Target extends Target {

    private static final Logger log = LoggerFactory.getLogger(Ipv4Target.class);

    private static final Pattern IPV4_CIDR_PATTERN = Pattern.compile(
            "^(?<address>(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\." +
                    "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\." +
                    "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\." +
                    "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9]))" +
                    "(?:/(?<cidr>3[0-2]|[12]?[0-9]))?$"
    );

    private final Inet4Address target;

    private final int cidr;

    /**
     * Creates an IPv4 target from a raw configuration value such as {@code "10.0.0.0/8"}.
     *
     * @param raw raw configuration input
     * @throws IllegalArgumentException if raw is not a valid IPv4[/cidr] value
     */
    Ipv4Target(String raw) {
        super(raw);

        Matcher m = IPV4_CIDR_PATTERN.matcher(address);
        if (!m.matches()) throw new IllegalArgumentException("Invalid IPv4 target: " + raw);

        this.cidr = m.group("cidr") != null ? parseInt(m.group("cidr")) : 32;

        this.target = toInet4Address(parseDottedQuadToInt(m.group("address")));
    }

    public static Optional<Target> tryCreate(String target) {
        try {
            return of(new Ipv4Target(target));
        } catch (IllegalArgumentException e) {
            log.debug("Error parsing {} as IPv4 target: {}", target, e.getMessage(), e);
            return empty();
        }
    }

    /**
     * Tests if the given peer {@link IpAddress} matches this IPv4 target.
     *
     * <p>If CIDR is:</p>
     * <ul>
     *   <li>{@code <= 0}: matches all IPv4 peers</li>
     *   <li>{@code >= 32}: exact match only</li>
     *   <li>otherwise: matches if peer is inside the configured subnet</li>
     * </ul>
     *
     * @param address peer IP
     * @return true if peer matches, otherwise false
     */
    @Override
    public boolean peerMatches(IpAddress address) {
        if (address == null) return false;
        if (address.version() != IPV4) return false;

        return matchesPrefix(target.getAddress(), address.getInetAddress().getAddress(), cidr);
    }

}
