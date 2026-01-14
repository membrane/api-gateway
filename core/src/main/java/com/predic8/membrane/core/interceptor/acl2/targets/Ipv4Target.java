package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.*;
import org.slf4j.*;

import java.net.*;
import java.util.Optional;
import java.util.regex.*;

import static com.predic8.membrane.core.interceptor.acl2.IpAddress.ipVersion.*;
import static com.predic8.membrane.core.util.NetworkUtil.*;
import static java.lang.Integer.*;
import static java.net.InetAddress.*;
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

    private final int mask;

    private final int network;

    /**
     * Creates an IPv4 target from a raw configuration value such as {@code "10.0.0.0/8"}.
     *
     * @param raw raw configuration input
     * @throws IllegalArgumentException if raw is not a valid IPv4[/cidr] value
     */
    public Ipv4Target(String raw) {
        super(raw.trim());

        if (address.isEmpty()) throw new IllegalArgumentException("Invalid IPv4 target: " + raw);

        Matcher m = IPV4_CIDR_PATTERN.matcher(address);
        if (!m.matches()) throw new IllegalArgumentException("Invalid IPv4 target: " + raw);

        this.cidr = m.group("cidr") != null ? parseInt(m.group("cidr")) : 32;

        int addrInt = parseDottedQuadToInt(m.group("address"));
        this.mask = maskOf(cidr);
        this.network = addrInt & mask;

        this.target = toInet4Address(addrInt);
    }

    public static Optional<Target> tryCreate(String raw) {
        try {
            return of(new Ipv4Target(raw));
        } catch (IllegalArgumentException e) {
            return empty();
        }
    }

    public Inet4Address getTarget() {
        return target;
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
        if (address.version() != IPV4) return false;
        if (cidr <= 0) return true;
        if (cidr >= 32) return target.equals(address.getInetAddress());

        return (bytesToInt(address.getInetAddress().getAddress()) & mask) == network;
    }

}
