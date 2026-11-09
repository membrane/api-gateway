/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.proxies;

import com.google.re2j.*;

import static com.predic8.membrane.core.proxies.ServiceProxyKey.createHostPattern;

/**
 * Matches host names against the host of an API or the DNS names of a certificate: space-separated
 * alternatives, in which '*' stands for one or more characters, see {@link ServiceProxyKey#createHostPattern(String)}.
 * <p>
 * The host name comes from the client, so the pattern is matched with RE2/J, which takes linear time
 * however many wildcards it has. Java's backtracking engine takes time to the power of the number of
 * wildcards on a host name that almost matches.
 */
public final class HostPattern {

    /**
     * The longest domain name DNS allows (RFC 1035, section 2.3.4). Longer host names never match.
     */
    static final int MAX_HOST_NAME_LENGTH = 255;

    private final Pattern pattern;

    public HostPattern(String host) {
        // Lowercasing ASCII ourselves instead of using CASE_INSENSITIVE: RE2/J would also fold
        // non-ASCII letters such as the Kelvin sign onto ASCII ones.
        pattern = Pattern.compile(createHostPattern(toLowerCaseAscii(host)));
    }

    public boolean matches(String hostName) {
        if (hostName.length() > MAX_HOST_NAME_LENGTH || containsLineTerminator(hostName))
            return false;
        return pattern.matcher(toLowerCaseAscii(hostName)).matches();
    }

    /**
     * RE2/J's '.' matches every line terminator but '\n', Java's matches none of them.
     */
    private static boolean containsLineTerminator(String s) {
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case '\n', '\r', '\u0085', ' ', ' ' -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    private static String toLowerCaseAscii(String s) {
        final var chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] >= 'A' && chars[i] <= 'Z')
                chars[i] += 'a' - 'A';
        }
        return new String(chars);
    }

    @Override
    public String toString() {
        return pattern.pattern();
    }
}
