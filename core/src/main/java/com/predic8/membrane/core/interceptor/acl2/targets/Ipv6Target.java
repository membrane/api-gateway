package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.acl2.IpAddress.ipVersion.IPV6;
import static com.predic8.membrane.core.util.NetworkUtil.removeBracketsIfPresent;
import static java.util.Optional.empty;
import static java.util.Optional.of;

/**
 * IPv6 target definition with optional CIDR prefix (e.g. {@code 2001:db8::/64} or {@code [2001:db8::]/64}).
 *
 * <p>This class validates incoming {@link IpAddress} peers against a configured IPv6 network/prefix.
 * The configured input is expected to be an IPv6 literal (optionally enclosed in brackets) optionally
 * followed by {@code /<cidr>}.</p>
 *
 * <p>Matching is done by comparing the first {@code cidr} bits:</p>
 * <ul>
 *   <li>Compare the first {@code cidr/8} full bytes for equality.</li>
 *   <li>If there are remaining bits, compare them with a partial-byte mask.</li>
 * </ul>
 */
public final class Ipv6Target extends Target {

    private static final Logger log = LoggerFactory.getLogger(Ipv6Target.class);

    /**
     * IPv6 literal with optional brackets and optional CIDR (0..128).
     * Parsing/validation of IPv6 textual forms is delegated to {@link InetAddress#getByName(String)}.
     */
    private static final Pattern IPV6_CIDR_PATTERN = Pattern.compile("^(?<address>\\[?[^/\\s]+]?)(?:/(?<cidr>12[0-8]|1[01][0-9]|[1-9]?[0-9]))?$");

    private final Inet6Address target;
    private final int cidr;

    /**
     * Creates an IPv6 target from a raw configuration value such as {@code "2001:db8::/64"}.
     *
     * @param raw raw configuration input
     * @throws IllegalArgumentException if raw is not a valid IPv6[/cidr] value
     */
    public Ipv6Target(String raw) {
        super(raw);

        if (address.isEmpty()) throw new IllegalArgumentException("Invalid IPv6 target: " + raw);

        Matcher m = IPV6_CIDR_PATTERN.matcher(address);
        if (!m.matches()) throw new IllegalArgumentException("Invalid IPv6 target: " + raw);

        InetAddress inet;
        try {
            inet = InetAddress.getByName(removeBracketsIfPresent(m.group("address")));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv6 target: " + raw, e);
        }

        if (!(inet instanceof Inet6Address inet6)) {
            throw new IllegalArgumentException("Invalid IPv6 target: " + raw);
        }
        this.target = inet6;

        String cidrGroup = m.group("cidr");
        this.cidr = cidrGroup != null ? Integer.parseInt(cidrGroup) : 128;
    }

    /**
     * Factory-style parser used by {@link Target#byMatch(String)}.
     * Returns {@link Optional#empty()} if the input cannot be parsed as an IPv6 target.
     */
    public static Optional<Target> tryCreate(String target) {
        try {
            return of(new Ipv6Target(target));
        } catch (IllegalArgumentException e) {
            log.debug("Error parsing {} as IPv6 target: {}", target, e.getMessage(), e);
            return empty();
        }
    }

    /**
     * Tests if the given peer {@link IpAddress} matches this IPv6 target.
     *
     * <p>If CIDR is:</p>
     * <ul>
     *   <li>{@code <= 0}: matches all IPv6 peers</li>
     *   <li>{@code >= 128}: exact match only</li>
     *   <li>otherwise: matches if peer is inside the configured prefix</li>
     * </ul>
     *
     * @param address peer IP
     * @return true if peer matches, otherwise false
     */
    @Override
    public boolean peerMatches(IpAddress address) {
        if (address == null) return false;
        if (address.version() != IPV6) return false;

        if (cidr <= 0) return true;
        if (cidr >= 128) return target.equals(address.getInetAddress());

        byte[] a = target.getAddress();
        byte[] b = address.getInetAddress().getAddress();

        int fullBytes = cidr / 8;
        int remainingBits = cidr % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (a[i] != b[i]) return false;
        }

        if (remainingBits == 0) return true;

        int partialMask = 0xFF << (8 - remainingBits);
        return (a[fullBytes] & partialMask) == (b[fullBytes] & partialMask);
    }


    public Inet6Address getTarget() {
        return target;
    }

    public int getCidr() {
        return cidr;
    }
}
