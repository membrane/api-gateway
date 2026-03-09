package com.predic8.membrane.core.util.wsdl.parser;

import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.wsdl.parser.Binding.*;

import java.util.*;

public class WSDLParserContext {

    private Definitions definitions;
    private Resolver resolver;
    private String basePath;
    private List<String> visitedLocations = new ArrayList<>();

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
