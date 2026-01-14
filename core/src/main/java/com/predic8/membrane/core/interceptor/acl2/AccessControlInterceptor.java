package com.predic8.membrane.core.interceptor.acl2;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.acl2.rules.AccessRule;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.router.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @topic 3. Security
 * @description
 * <p>Applies access control rules to incoming requests based on the peer address.</p>
 *
 * <p>The interceptor evaluates the configured child rules in order and uses the first rule that matches the peer to
 * decide whether the request is permitted. If no rule matches, access is denied.</p>
 *
 * <p>Rules can match on IPv4/IPv6 (optionally with CIDR prefix) or on a hostname pattern. Hostname matching requires
 * the peer hostname to be resolved and is performed only when at least one configured rule uses a hostname target.</p>
 *
 * @yaml
 * <pre><code>
 * accessControl:
 *   - allow: "10.0.0.0/8"
 *   - deny: "0.0.0.0/0"
 * </code></pre>
 */
@MCElement(name = "accessControl", noEnvelope = true)
public class AccessControlInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AccessControlInterceptor.class);

    private final AccessControl accessControl = new AccessControl();

    @Override
    public void init(Router router, Proxy proxy) {
        super.init(router, proxy);
        accessControl.init(router.getDnsCache());
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        String remoteIp = exc.getRemoteAddrIp();
        if (!accessControl.isPermitted(remoteIp)) {
            setResponseToAccessDenied(exc);
            log.debug("Access denied. remoteIp={} method={} uri={}", remoteIp, exc.getRequest().getMethod(), exc.getRequestURI());
            return ABORT;
        }
        return CONTINUE;
    }

    // Copied from old ACL as is
    private void setResponseToAccessDenied(Exchange exc) {
        security(false, getDisplayName())
                .title("Access Denied")
                .status(401)
                .addSubSee("authorization-denied")
                .buildAndSetResponse(exc);
    }

    /**
     * @description
     * <p>Configures the ordered list of access rules that will be evaluated for each request.</p>
     *
     * <p>Rules are processed in the given order ("first decision wins"). Each rule references a target value that can be
     * an IPv4/IPv6 literal (optionally with CIDR prefix) or a hostname pattern.</p>
     */
    @MCChildElement
    public void setRules(List<AccessRule> rules) {
        accessControl.setRules(rules);
    }

    public List<AccessRule> getRules() {
        return accessControl.getRules();
    }
}
