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

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.XMLSupport;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.router.Router;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static java.util.stream.Stream.concat;

@MCElement(name="xmlConfig", component = true)
public class XmlConfig {

    private Namespaces namespaces;

    /**
     * The namespaces of {@code local} plus those of the global xmlConfig whose prefix {@code local}
     * does not declare. Like nested <code>xmlns</code> declarations in XML, a local prefix shadows
     * the global one with the same name.
     */
    public static @Nullable XmlConfig withGlobal(@Nullable XmlConfig local, @Nullable Router router) {
        if (router == null || router.getRegistry() == null)
            return local;
        return merge(local, router.getRegistry().getBean(XmlConfig.class).orElse(null));
    }

    /** Plugin-local declarations override API declarations. Global defaults are added by withGlobal. */
    public static @Nullable XmlConfig forInterceptor(@Nullable Interceptor interceptor) {
        if (interceptor == null)
            return null;
        return withApi(interceptor instanceof XMLSupport xml ? xml.getXmlConfig() : null, interceptor.getProxy());
    }

    /** Merge without modifying either configured scope. */
    public static @Nullable XmlConfig withApi(@Nullable XmlConfig local, @Nullable Proxy proxy) {
        return merge(local, proxy instanceof APIProxy api ? api.getXmlConfig() : null);
    }

    static @Nullable XmlConfig merge(@Nullable XmlConfig local, @Nullable XmlConfig global) {
        final var globalNamespaces = namespaceList(global);
        if (globalNamespaces.isEmpty())
            return local;
        final var localNamespaces = namespaceList(local);
        if (localNamespaces.isEmpty())
            return global;

        final var localPrefixes = localNamespaces.stream().map(Namespaces.Namespace::getPrefix).toList();
        final var merged = new Namespaces();
        merged.setNamespaces(concat(localNamespaces.stream(),
                globalNamespaces.stream().filter(ns -> !localPrefixes.contains(ns.getPrefix()))).toList());
        final var xmlConfig = new XmlConfig();
        xmlConfig.setNamespaces(merged);
        return xmlConfig;
    }

    private static List<Namespaces.Namespace> namespaceList(@Nullable XmlConfig xmlConfig) {
        if (xmlConfig == null || xmlConfig.getNamespaces() == null || xmlConfig.getNamespaces().getNamespaces() == null)
            return List.of();
        return xmlConfig.getNamespaces().getNamespaces();
    }

    @MCChildElement
    public void setNamespaces(Namespaces namespaces) {
        this.namespaces = namespaces;
    }

    public Namespaces getNamespaces() {
        return namespaces;
    }
}