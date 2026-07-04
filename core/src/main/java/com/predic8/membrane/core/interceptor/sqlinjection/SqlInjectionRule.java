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

package com.predic8.membrane.core.interceptor.sqlinjection;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A single SQL-injection detection rule transpiled from an OWASP CRS
 * REQUEST-942 {@code @rx} rule.
 *
 * @param id             the original CRS rule id, kept for attribution and logging
 * @param paranoiaLevel  CRS paranoia level 1-4; higher = more aggressive, more false positives
 * @param transforms     normalisations applied to the input before matching
 * @param message        human-readable description of what the rule detects
 * @param regex          the detection pattern
 * @param requires       optional second pattern (CRS chained rule); when present both must match
 */
public record SqlInjectionRule(String id, int paranoiaLevel, List<Transformation> transforms,
                               String message, Pattern regex, Pattern requires) {

    /** @return true if the (normalised) input violates this rule. */
    public boolean matches(String input) {
        String normalised = Transformation.applyAll(input, transforms);
        if (!regex.matcher(normalised).find())
            return false;
        return requires == null || requires.matcher(normalised).find();
    }
}
