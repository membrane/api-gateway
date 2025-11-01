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

package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.annot.*;

import javax.xml.namespace.*;
import java.util.*;

import static javax.xml.XMLConstants.NULL_NS_URI;

@MCElement(name="namespaces", topLevel = true)
public class Namespaces {

    //NamespaceContext nsContext;
    private List<Namespace> namespaces;
    private final NamespaceContextImpl nsContext = new NamespaceContextImpl();

    public NamespaceContext getNamespaceContext() {
        return nsContext;
    }

    /**
     * @description Defines a regex and a replacement for the rewriting of the URI.
     */
    @MCChildElement(allowForeign = false)
    public void setNamespace(List<Namespace> namespace) {
        this.namespaces = namespace;
    }

    public List<Namespace> getNamespace() {
        return namespaces;
    }

    @MCElement(name = "namespace", topLevel = false, id = "xml-namespace")
    public static class Namespace {

        public String prefix;
        public String uri;

        public Namespace() {
        }

        public String getPrefix() {
            return prefix;
        }

        @MCAttribute
        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getUri() {
            return uri;
        }

        @MCAttribute
        public void setUri(String uri) {
            this.uri = uri;
        }
    }

    class NamespaceContextImpl implements NamespaceContext {

        @Override
        public String getNamespaceURI(String prefix) {
            return namespaces.stream()
                    .filter(ns -> prefix.equals(ns.prefix))
                    .findFirst()
                    .map(ns -> ns.uri)
                    .orElse(NULL_NS_URI);
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return namespaces.stream()
                    .filter(ns -> namespaceURI.equals(ns.uri))
                    .map(ns -> ns.prefix)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return namespaces.stream()
                    .filter(ns -> namespaceURI.equals(ns.uri))
                    .map(ns -> ns.prefix)
                    .iterator();
        }
    }
}
