package com.predic8.membrane.core.util.config.allowdeny;

import com.predic8.membrane.annot.MCElement;

/**
 * @description Denies values matching the configured regular expression.
 */
@MCElement(name = "deny", collapsed = true, component = false, id = "deny-rule")
public class Deny extends Rule {

    @Override
    public boolean permits() {
        return false;
    }

    @Override
    public String toString() {
        return "Deny{pattern=%s}".formatted(getPattern());
    }
}
