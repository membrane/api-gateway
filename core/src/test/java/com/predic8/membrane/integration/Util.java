/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.integration;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

import java.util.Arrays;

public class Util {
    public static HttpRouter basicRouter(Rule... rules){
        HttpRouter router = new HttpRouter();
        router.getTransport().setForceSocketCloseOnHotDeployAfter(1000);
        router.setHotDeploy(false);

        Arrays.stream(rules).forEach(rule -> router.getRuleManager().addProxy(rule, RuleManager.RuleDefinitionSource.MANUAL));

        router.start();
        return router;
    }

    public static Rule createServiceProxy(int listenPort, Interceptor... interceptors){
        return createServiceProxy(listenPort,null,interceptors);
    }

    public static Rule createServiceProxy(int listenPort, AbstractServiceProxy.Target target, Interceptor... interceptors){
        if (target == null)
            target = new AbstractServiceProxy.Target(null, -1);

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(listenPort), null, -1);
        sp.setTarget(target);

        for (Interceptor interceptor : interceptors)
            sp.getInterceptors().add(interceptor);

        return sp;
    }
}