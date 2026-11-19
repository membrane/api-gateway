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

package com.predic8.membrane.core.util.wsdl.parser;

import com.predic8.membrane.core.resolver.*;

import java.util.*;

public record WSDLParserContext(Definitions definitions, Resolver resolver, String basePath,
                                Set<LoadedSchema> loadedSchemas) {

    /**
     * A schema document as loaded: where it comes from, and the namespace its declarations end up
     * in. A document without targetNamespace is loaded once for every namespace that includes it.
     */
    public record LoadedSchema(String location, String namespace) {}

    public WSDLParserContext definitions(Definitions definitions) {
        return new WSDLParserContext(definitions, resolver, basePath, loadedSchemas);
    }

    public WSDLParserContext basePath(String basePath) {
        return new WSDLParserContext(definitions, resolver, basePath, loadedSchemas);
    }

    public WSDLParserContext resolver(Resolver resolver) {
        return new WSDLParserContext(definitions, resolver, basePath, loadedSchemas);
    }

    /**
     * Records that the schema document at {@code location} is loaded into {@code namespace}.
     * {@code loadedSchemas} is shared by all contexts of one parse on purpose: it holds every
     * document already included or imported, which also stops include and import cycles.
     *
     * @return false if that document was already loaded into that namespace
     */
    public boolean load(String location, String namespace) {
        return loadedSchemas.add(new LoadedSchema(location, namespace));
    }
}
