/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.config.xml;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.router.*;
import org.jetbrains.annotations.*;

@MCElement(name="xmlConfig", component = true)
public class XmlConfig {

    private Namespaces namespaces;

    /**
     * The global xmlConfig declared in components, or null if there is none.
     */
    public static @Nullable XmlConfig global(@Nullable Router router) {
        if (router == null || router.getRegistry() == null)
            return null;
        return router.getRegistry().getBean(XmlConfig.class).orElse(null);
    }

    /**
     * The local xmlConfig if there is one, otherwise the global one. A local xmlConfig replaces
     * the global one, they are not merged.
     */
    public static @Nullable XmlConfig localOrGlobal(@Nullable XmlConfig local, @Nullable Router router) {
        return local != null ? local : global(router);
    }

    /**
     * The xmlConfig of the interceptor if it has one, otherwise the global one of its router.
     */
    public static @Nullable XmlConfig localOrGlobal(@Nullable Interceptor interceptor) {
        if (interceptor == null)
            return null;
        return localOrGlobal(interceptor instanceof XMLSupport xs ? xs.getXmlConfig() : null, interceptor.getRouter());
    }

    @MCChildElement
    public void setNamespaces(Namespaces namespaces) {
        this.namespaces = namespaces;
    }

    public Namespaces getNamespaces() {
        return namespaces;
    }
}