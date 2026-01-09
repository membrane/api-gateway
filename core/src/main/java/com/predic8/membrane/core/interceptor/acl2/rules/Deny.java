package com.predic8.membrane.core.interceptor.acl2.rules;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;

import java.util.Optional;

@MCElement(name = "deny")
public class Deny extends AccessRule {

    @Override
    boolean permitPeer() {
        return false;
    }
}
