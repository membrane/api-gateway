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

package com.predic8.membrane.core.interceptor.acl.rules;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.interceptor.acl.*;
import com.predic8.membrane.core.interceptor.acl.AccessControl.*;
import com.predic8.membrane.core.interceptor.acl.targets.*;
import com.predic8.membrane.core.util.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.acl.targets.Target.*;

public abstract class AccessRule {

    protected Target target;

    public Optional<AccessDecision> apply(IpAddress address) {
        if (target.peerMatches(address)) {
            return Optional.of(new AccessDecision(permitPeer(), address));
        }

        return Optional.empty();
    }

    abstract boolean permitPeer();

    public static Allow allow(String target) {
        var allow = new Allow();
        allow.setTarget(target);
        return allow;
    }

    public static Deny deny(String target) {
        var deny = new Deny();
        deny.setTarget(target);
        return deny;
    }


    /**
     * @description The IPv4, IPv6 or hostname regex pattern
     * @example "127.0.0.1/24" or "^local.*$"
     */
    @Required
    @MCAttribute
    public void setTarget(String target) {
        if (target == null || target.trim().isEmpty()) throw new ConfigurationException("target cannot be empty");
        try {
            this.target = byMatch(target.trim());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }

    public String getTarget() {
        return target.toString();
    }

    public boolean isHostnameRule() {
        return target instanceof com.predic8.membrane.core.interceptor.acl.targets.HostnameTarget;
    }
}
