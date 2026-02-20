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

package com.predic8.membrane.core.util;

import java.util.*;

import static com.predic8.membrane.core.util.URIValidationUtil.*;

/**
 * Validates URI components (RFC 3986) with an optional extension to allow '{' and '}' in paths.
 * <p>
 * Intended for use with {@link URI} which keeps raw components.
 */
public final class UriIllegalCharacterDetector {

    private UriIllegalCharacterDetector() {
    }

    public static void validateAll(URI uri, Options options) {
        validateAll(uri.getScheme(), uri.getAuthority(), uri.getUserInfo(), uri.getRawPath(), uri.getRawQuery(), uri.getRawFragment(), options);
    }

    public static void validateAll(String scheme,
                                   String authority,
                                   String userInfo,
                                   String rawPath,
                                   String rawQuery,
                                   String rawFragment,
                                   Options options) {
        Objects.requireNonNull(options, "options");

        if (options.skipAllValidation) {
            return;
        }

        // Safety-critical checks can still be applied even if relaxed.
        validateNoControlsOrSpaces(rawPath, "path");
        validateNoControlsOrSpaces(rawQuery, "query");
        validateNoControlsOrSpaces(rawFragment, "fragment");
        validateNoControlsOrSpaces(authority, "authority");
        validateNoControlsOrSpaces(scheme, "scheme");

        validatePctEncoding(rawPath, "path");
        validatePctEncoding(rawQuery, "query");
        validatePctEncoding(rawFragment, "fragment");
        validatePctEncoding(authority, "authority");
        validateUserInfo(userInfo, options);

        // scheme never contains '%' in valid RFC 3986; no need to check percent there.

        if (options.strictRfc3986) {
            validateScheme(scheme);
            // Authority is not validated here, cause it is done in URI with HostAndPort
            validateRfc3986Path(rawPath, options.allowBracesInPath);
            validateRfc3986QueryOrFragment(rawQuery, "query", options);
            validateRfc3986QueryOrFragment(rawFragment, "fragment", options);
        }
    }

    public static final class Options {

        private boolean skipAllValidation = false;
        private boolean strictRfc3986 = true;
        private boolean allowBracesInPath = false;
        private boolean allowBracesInQueryAndFragment = false;
        private boolean allowBracesInUserInfo = false;

        private Options() {}

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private final Options o = new Options();

            /**
             * If true, no validation checks are performed at all.
             */
            public Builder skipAllValidation(boolean value) {
                o.skipAllValidation = value;
                return this;
            }

            /**
             * If true, enforce RFC 3986 component character rules
             * (plus configured extensions).
             * If false, only control/space and percent-encoding checks are applied.
             */
            public Builder strictRfc3986(boolean value) {
                o.strictRfc3986 = value;
                return this;
            }

            /**
             * Membrane extension: allow '{' and '}' inside the path component.
             */
            public Builder allowBracesInPath(boolean value) {
                o.allowBracesInPath = value;
                return this;
            }

            /**
             * If true, allow '{' and '}' in query and fragment components.
             * Default is false.
             */
            public Builder allowBracesInQueryAndFragment(boolean value) {
                o.allowBracesInQueryAndFragment = value;
                return this;
            }

            /**
             * If true, allow '{' and '}' in the user-info component.
             * Default is false.
             */
            public Builder allowBracesInUserInfo(boolean value) {
                o.allowBracesInUserInfo = value;
                return this;
            }

            public Options build() {
                return o;
            }
        }
    }

    private static void validateScheme(String scheme) {
        if (scheme == null) return;

        // RFC 3986: scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
        if (scheme.isEmpty()) {
            throw new IllegalArgumentException("Illegal scheme: empty.");
        }
        char first = scheme.charAt(0);
        if (!isAlpha(first)) {
            throw new IllegalArgumentException("Illegal scheme: must start with ALPHA: " + scheme);
        }
        for (int i = 1; i < scheme.length(); i++) {
            char c = scheme.charAt(i);
            if (!(isAlpha(c) || isDigit(c) || c == '+' || c == '-' || c == '.')) {
                throw new IllegalArgumentException("Illegal character in scheme at index %d: '%s'".formatted(i, c));
            }
        }
    }

    private static void validateUserInfo(String userInfo, Options options) {
        if (userInfo == null) return;

        // RFC 3986: userinfo = *( unreserved / pct-encoded / sub-delims / ":" )
        for (int i = 0; i < userInfo.length(); i++) {
            char c = userInfo.charAt(i);
            if (c == '%') continue;
            if (options.allowBracesInUserInfo && (c == '{' || c == '}')) continue;
            if (!(isUnreserved(c) || isSubDelims(c) || c == ':')) {
                throw new IllegalArgumentException("Illegal character in user-info at index " + i + ": '" + c + "'");
            }
        }
    }

    private static void validateNoControlsOrSpaces(String s, String component) {
        if (s == null) return;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // Reject CTLs and space. (Includes tabs, newlines, etc.)
            if (c <= 0x20 || c == 0x7F) {
                throw new IllegalArgumentException("Illegal character in %s at index %d: 0x%s"
                        .formatted(component, i, Integer.toHexString(c)));
            }
        }
    }

    private static void validatePctEncoding(String s, String component) {
        if (s == null) return;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '%') {
                if (i + 2 >= s.length() || !isHex(s.charAt(i + 1)) || !isHex(s.charAt(i + 2))) {
                    throw new IllegalArgumentException("Invalid percent-encoding in %s at index %d".formatted(component, i));
                }
                i += 2;
            }
        }
    }

    private static void validateRfc3986Path(String path, boolean allowBracesInPath) {
        if (path == null) return;

        // path = *( "/" / pchar )
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '/') continue;
            if (c == '%') continue; // pct-encoding validated structurally
            if (allowBracesInPath && (c == '{' || c == '}')) continue;
            if (!isPchar(c)) {
                throw new IllegalArgumentException("Illegal character in path at index " + i + ": '" + c + "'");
            }
        }
    }

    private static void validateRfc3986QueryOrFragment(String s, String component, Options options) {
        if (s == null) return;

        // query/fragment = *( pchar / "/" / "?" )
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '?') continue;
            if (c == '%') continue;
            if (options.allowBracesInQueryAndFragment && (c == '{' || c == '}')) continue;
            if (!isPchar(c)) {
                throw new IllegalArgumentException("Illegal character in " + component + " at index " + i + ": '" + c + "'");
            }
        }
    }


}
