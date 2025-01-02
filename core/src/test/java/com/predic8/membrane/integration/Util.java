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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;

import java.util.*;

public class Util {
    public static HttpRouter basicRouter(Proxy... proxies){
        HttpRouter router = new HttpRouter();
        router.getTransport().setForceSocketCloseOnHotDeployAfter(1000);
        router.setHotDeploy(false);

        Arrays.stream(proxies).forEach(rule -> router.getRuleManager().addProxy(rule, RuleManager.RuleDefinitionSource.MANUAL));

        router.start();
        return router;
    }

    public static Proxy createServiceProxy(int listenPort, Interceptor... interceptors){
        return createServiceProxy(listenPort,null,interceptors);
    }

    public static Proxy createServiceProxy(int listenPort, AbstractServiceProxy.Target target, Interceptor... interceptors){
        if (target == null)
            target = new AbstractServiceProxy.Target(null, -1);

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(listenPort), null, -1);
        sp.setTarget(target);

        for (Interceptor interceptor : interceptors)
            sp.getInterceptors().add(interceptor);

        return sp;
    }
}
