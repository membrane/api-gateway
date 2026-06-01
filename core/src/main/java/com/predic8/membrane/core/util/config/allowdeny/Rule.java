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
