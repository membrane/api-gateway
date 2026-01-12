package com.predic8.membrane.core.interceptor.acl2.rules;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.acl2.targets.Target;
import com.predic8.membrane.core.util.ConfigurationException;

import java.util.Optional;

@MCElement(name = "accessRule", collapsed = true)
public abstract class AccessRule {

    protected Target target;

    public Optional<Boolean> apply(Exchange exc) {
        if (target.peerMatches(exc)) return Optional.of(permitPeer());

        return Optional.empty();
    }

    abstract boolean permitPeer();

    @MCAttribute
    public void setTarget(String target) {
        try {
            this.target = Target.byMatch(target);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }

    public String getTarget() {
        return target.toString();
    }
}
