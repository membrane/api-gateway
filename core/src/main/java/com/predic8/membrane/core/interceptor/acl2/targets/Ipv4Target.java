package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.acl2.IpAddress.ipVersion.IPV4;
import static java.lang.Integer.parseInt;
import static java.net.InetAddress.getByAddress;

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

    public Inet4Address getTarget() {
        return target;
    }

    public static boolean accepts(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return false;
        return IPV4_CIDR_PATTERN.matcher(s).matches();
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

    private static int maskOf(int prefix) {
        if (prefix <= 0) return 0;
        if (prefix >= 32) return 0xFFFFFFFF;
        return (int) (0xFFFFFFFFL << (32 - prefix));
    }

    /**
     * Converts a dotted-quad IPv4 string into a 32-bit integer.
     * Assumes the input has already been validated by {@link #IPV4_CIDR_PATTERN}.
     */
    private static int parseDottedQuadToInt(String s) {
        String[] p = s.split("\\.", 4);
        return (parseInt(p[0]) << 24)
                | (parseInt(p[1]) << 16)
                | (parseInt(p[2]) << 8)
                | parseInt(p[3]);
    }

    private static int bytesToInt(byte[] b) {
        return ((b[0] & 0xFF) << 24)
                | ((b[1] & 0xFF) << 16)
                | ((b[2] & 0xFF) << 8)
                | (b[3] & 0xFF);
    }

    private static Inet4Address toInet4Address(int ip) {
        byte[] bytes = new byte[]{
                (byte) (ip >>> 24),
                (byte) (ip >>> 16),
                (byte) (ip >>> 8),
                (byte) ip
        };
        try {
            return (Inet4Address) getByAddress(bytes);
        } catch (UnknownHostException e) {
            // should never happen
            throw new IllegalStateException(e);
        }
    }
}
