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

import com.predic8.membrane.core.graphql.model.*;
import com.predic8.membrane.core.resolver.*;

import java.util.*;

public class WSDLParserContext {

    private final Definitions definitions;
    private final Resolver resolver;
    private final String basePath;
    private final List<String> visitedLocations;

    public WSDLParserContext(Definitions wsdl, Resolver resolver, String basePath, List<String> visitedLocations) {
        this.definitions = wsdl;
        this.resolver = resolver;
        this.basePath = basePath;
        this.visitedLocations = visitedLocations;
    }

    public WSDLParserContext definitions(Definitions definitions) {
        return new WSDLParserContext(definitions, resolver, basePath, visitedLocations);
    }

    public WSDLParserContext basePath(String basePath) {
        visitedLocations.add(basePath);
        return new WSDLParserContext(definitions, resolver, basePath, visitedLocations);
    }

    public WSDLParserContext resolver(Resolver resolver) {
        return new WSDLParserContext(definitions, resolver, basePath, visitedLocations);
    }

    public List<String> getVisitedLocations() {
        return visitedLocations;
    }

    public Definitions getDefinitions() {
        return definitions;
    }

    public Resolver getResolver() {
        return resolver;
    }

    public String getBasePath() {
        return basePath;
    }
}
