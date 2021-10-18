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
package com.predic8.membrane.core.rules;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLProvider;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;

@MCElement(name="internalProxy")
public class InternalProxy extends AbstractProxy{
    private AbstractServiceProxy.Target target;
    private SSLContext sslOutboundContext;

    public InternalProxy() {
        key = new AbstractRuleKey(-1,null){

        };
    }

    @Override
    public void init() throws Exception {
        super.init();
        if (target.getSslParser() != null)
            setSslOutboundContext(new StaticSSLContext(target.getSslParser(), router.getResolverMap(), router.getBaseLocation()));
    }

    @Override
    protected AbstractProxy getNewInstance() {
        return new InternalProxy();
    }

    @MCChildElement(order=150)
    public void setTarget(AbstractServiceProxy.Target target) {
        this.target = target;
    }

    public AbstractServiceProxy.Target getTarget() {
        return target;
    }

    @Override
    public SSLProvider getSslOutboundContext() {
        return sslOutboundContext;
    }

    protected void setSslOutboundContext(SSLContext sslOutboundContext) {
        this.sslOutboundContext = sslOutboundContext;
    }

}
