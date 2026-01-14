package com.predic8.membrane.core.interceptor.acl2.rules;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.interceptor.acl2.IpAddress;
import com.predic8.membrane.core.interceptor.acl2.targets.*;
import com.predic8.membrane.core.util.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.acl2.targets.Target.byMatch;

public abstract class AccessRule {

    protected Target target;

    public Optional<Boolean> apply(IpAddress address) {
        if (target.peerMatches(address)) return Optional.of(permitPeer());

        return Optional.empty();
    }

    abstract boolean permitPeer();

    @MCAttribute
    public void setTarget(String target) {
        if (target == null || target.isEmpty()) throw new ConfigurationException("target cannot be empty");
        try {
            this.target = byMatch(target.trim());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }

    public String getTarget() {
        return target.toString();
    }
}
