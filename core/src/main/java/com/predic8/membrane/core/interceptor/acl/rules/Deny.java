package com.predic8.membrane.core.interceptor.acl.rules;

import com.predic8.membrane.annot.MCElement;

/**
 * @description
 * <p>Denies requests from peers matching the configured target.</p>
 */
@MCElement(name = "deny", collapsed = true)
public class Deny extends AccessRule {

    @Override
    boolean permitPeer() {
        return false;
    }
}
