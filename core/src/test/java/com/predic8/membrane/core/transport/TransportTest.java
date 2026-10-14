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

package com.predic8.membrane.core.transport;

import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.RuleMatchingInterceptor;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.transport.http.HttpTransport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class TransportTest {

    @Test
    void usesInterceptorDeclaredAsComponentInRegistry() {
        final var ruleMatching = new RuleMatchingInterceptor();
        ruleMatching.setxForwardedForEnabled(false);

        final var router = new DefaultRouter();
        router.setExchangeStore(new ForgetfulExchangeStore());
        router.getRegistry().register("ruleMatching", ruleMatching);

        final var transport = new HttpTransport();
        transport.init(router);

        final var used = transport.getFirstInterceptorOfType(RuleMatchingInterceptor.class).orElseThrow();
        assertSame(ruleMatching, used);
        assertFalse(used.isxForwardedForEnabled());
    }
}
