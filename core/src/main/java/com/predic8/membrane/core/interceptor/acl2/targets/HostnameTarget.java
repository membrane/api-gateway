package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.util.Optional.empty;
import static java.util.Optional.of;

/**
 * Hostname target definition backed by a regular expression (e.g. {@code ^.*\\.example\\.com$}).
 *
 * <p>This class validates incoming {@link IpAddress} peers against a configured hostname regex.
 * The configured input is expected to be a regular expression. Matching is performed against
 * {@link IpAddress#getHostname()}.</p>
 */
public final class HostnameTarget extends Target {

    private static final Logger log = LoggerFactory.getLogger(HostnameTarget.class);

    private static final Pattern IPV4_LIKE = Pattern.compile("^\\d+(?:\\.\\d+){1,3}(?:/\\d+)?$");
    private static final Pattern IPV6_LIKE = Pattern.compile("^\\[?[0-9a-fA-F:]+]?(?:/\\d+)?$");

    private final Pattern hostnamePattern;

    /**
     * Creates a hostname target from a raw configuration value.
     *
     * @param raw a regex used to match a peer hostname
     * @throws IllegalArgumentException if raw is blank or not a valid regex
     */
    public HostnameTarget(String raw) {
        super(raw);

        if (isIpLike(address)) {
            throw new IllegalArgumentException("Hostname regex must not be an IP literal/CIDR: " + raw);
        }

        try {
            this.hostnamePattern = Pattern.compile(address);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid hostname regex: " + raw, e);
        }
    }

    public static Optional<Target> tryCreate(String target) {
        try {
            return of(new HostnameTarget(target));
        } catch (IllegalArgumentException e) {
            log.debug("Error parsing {} as hostname target: {}", target, e.getMessage(), e);
            return empty();
        }
    }

    /**
     * Tests if the given peer {@link IpAddress} matches this hostname regex.
     *
     * @param address peer IP (with optional hostname set)
     * @return true if hostname matches, otherwise false
     */
    @Override
    public boolean peerMatches(IpAddress address) {
        if (address == null) return false;
        return hostnamePattern.matcher(address.getHostname()).matches();
    }

    private static boolean isIpLike(String s) {
        return (s.indexOf('.') >= 0 && IPV4_LIKE.matcher(s).matches())
                || (s.indexOf(':') >= 0 && IPV6_LIKE.matcher(s).matches());
    }
}
