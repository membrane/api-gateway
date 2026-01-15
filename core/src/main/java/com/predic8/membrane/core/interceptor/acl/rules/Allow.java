package com.predic8.membrane.core.interceptor.acl.rules;

import com.predic8.membrane.annot.MCElement;

/**
 * @description
 * <p>Permits requests from peers matching the configured target.</p>
 */
@MCElement(name = "allow", collapsed = true)
public class Allow extends AccessRule {

    @Override
    boolean permitPeer() {
        return true;
    }
}
