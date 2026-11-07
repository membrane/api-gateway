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
package com.predic8.membrane.core.interceptor.administration.v1;

import java.util.*;

/**
 * The caller of the admin API and its roles.
 */
record Principal(boolean authenticated, String username, Set<String> roles, boolean readOnly, boolean canControlNodes) {

    static final String VIEWER = "viewer";
    static final String ADMIN = "admin";
    static final String NODE_OPERATOR = "nodeOperator";

    /**
     * The caller when no users are configured: behaves like the old console, so node control is allowed even when the
     * console is read-only.
     */
    static Principal anonymous(boolean consoleReadOnly) {
        final var roles = consoleReadOnly
                ? Set.of(VIEWER, NODE_OPERATOR)
                : Set.of(VIEWER, ADMIN, NODE_OPERATOR);
        return of(false, null, roles, consoleReadOnly);
    }

    static Principal of(boolean authenticated, String username, Set<String> roles, boolean consoleReadOnly) {
        final var sorted = Collections.unmodifiableSet(new TreeSet<>(roles));
        return new Principal(authenticated, username, sorted,
                consoleReadOnly || !roles.contains(ADMIN),
                roles.contains(NODE_OPERATOR));
    }

    boolean may(Access access) {
        return switch (access) {
            case READ -> true;
            case ADMIN -> !readOnly;
            case NODE_CONTROL -> canControlNodes;
        };
    }
}
