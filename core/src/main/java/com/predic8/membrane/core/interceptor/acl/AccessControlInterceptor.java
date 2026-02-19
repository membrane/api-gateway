/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.acl;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.acl.rules.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @topic 3. Security and Validation
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
 * - accessControl:
 *     - allow: "10.0.0.0/8"
 *     - deny: "0.0.0.0/0"
 * </code></pre>
 */
@MCElement(name = "accessControl", noEnvelope = true)
public class AccessControlInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AccessControlInterceptor.class);

    private final AccessControl accessControl = new AccessControl();

    @Override
    public void init() {
        super.init();
        accessControl.init(router.getDnsCache());
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        String remoteIp = exc.getRemoteAddrIp();
        var decision = accessControl.isPermitted(remoteIp);
        if (!decision.permitted()) {
            setResponseToAccessDenied(exc);
            log.info("Access denied from {}", decision.address());
            return ABORT;
        }
        return CONTINUE;
    }

    // Copied from old ACL as is
    private void setResponseToAccessDenied(Exchange exc) {
        security(false, getDisplayName())
                .title("Access Denied")
                .status(403)
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
