package com.predic8.membrane.core.interceptor.acl2.rules;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;

import java.util.Optional;

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
