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

package com.predic8.membrane.core.util.xml;

import com.predic8.membrane.core.config.xml.XmlConfig;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

public final class XPathUtil {

    /**
     * XPathFactory is not thread-safe!
     */
    private static final ThreadLocal<XPathFactory> FACTORY = ThreadLocal.withInitial(XPathFactory::newInstance);

    private XPathUtil() {
    }

    public static XPath newXPath() {
        return FACTORY.get().newXPath();
    }

    public static XPath newXPath(NamespaceContext context) {
        XPath xpath = FACTORY.get().newXPath();
        if (context != null) {
            xpath.setNamespaceContext(context);
        }
        return xpath;
    }

    public static XPath newXPath(XmlConfig xmlConfig) {
        if (xmlConfig == null || xmlConfig.getNamespaces() == null) {
            return newXPath();
        }
        return newXPath(xmlConfig.getNamespaces().getNamespaceContext());
    }
}
