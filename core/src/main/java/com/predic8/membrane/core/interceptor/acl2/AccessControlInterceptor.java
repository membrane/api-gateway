package com.predic8.membrane.core.interceptor.acl2;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.acl2.rules.AccessRule;

import java.util.List;
import java.util.Optional;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "accessControl")
public class AccessControlInterceptor extends AbstractInterceptor {

    private List<AccessRule> rules;

    @Override
    public Outcome handleRequest(Exchange exc) {
        Optional<Boolean> permit = evaluatePermission(exc, rules);

        if (permit.isEmpty() || !permit.get()) {
            setResponseToAccessDenied(exc);
            return ABORT;
        }

        return CONTINUE;
    }

    static Optional<Boolean> evaluatePermission(Exchange exc, List<AccessRule> rules) {
        Optional<Boolean> permit = Optional.empty();
        for(AccessRule rule : rules) {
            permit = rule.apply(exc);
            if(permit.isPresent()) break;
        }
        return permit;
    }

    // Copied from old ACL as is
    private void setResponseToAccessDenied(Exchange exc) {
        security(false, getDisplayName())
                .title("Access Denied")
                .status(401)
                .addSubSee("authorization-denied")
                .buildAndSetResponse(exc);
    }

    @MCChildElement
    public void setRules(List<AccessRule> rules) {
        this.rules = rules;
    }

    public List<AccessRule> getRules() {
        return rules;
    }
}
