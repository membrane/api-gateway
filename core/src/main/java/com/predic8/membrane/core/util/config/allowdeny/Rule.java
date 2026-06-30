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

package com.predic8.membrane.core.util.config.allowdeny;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.util.ConfigurationException;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Ordered allow/deny rule based on a regular expression.
 */
public abstract class Rule {

    private String pattern;
    private Pattern compiledPattern;

    public boolean matches(String probe) {
        if (probe == null) {
            return false;
        }
        return compiledPattern != null && compiledPattern.matcher(probe).matches();
    }

    public abstract boolean permits();

    /**
     * @description The regular expression matched against the input value.
     * @example "^rpc\\.(health|echo)$"
     */
    @Required
    @MCAttribute
    public void setPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new ConfigurationException("pattern must not be empty");
        }

        this.pattern = pattern.trim();
        try {
            compiledPattern = Pattern.compile(this.pattern);
        } catch (PatternSyntaxException e) {
            throw new ConfigurationException("Invalid regex pattern: " + this.pattern);
        }
    }

    public String getPattern() {
        return pattern;
    }

    @Deprecated
    public void setMethod(String method) {
        setPattern(method);
    }

    @Deprecated
    public String getMethod() {
        return getPattern();
    }
}
