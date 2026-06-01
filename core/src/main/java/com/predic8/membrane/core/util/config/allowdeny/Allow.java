package com.predic8.membrane.core.util.config.allowdeny;

import com.predic8.membrane.annot.MCElement;

/**
 * @description Permits values matching the configured regular expression.
 */
@MCElement(name = "allow", collapsed = true, component = false, id = "allow-rule")
public class Allow extends Rule {

    @Override
    public boolean permits() {
        return true;
    }

    @Override
    public String toString() {
        return "Allow{pattern=%s}".formatted(getPattern());
    }
}
