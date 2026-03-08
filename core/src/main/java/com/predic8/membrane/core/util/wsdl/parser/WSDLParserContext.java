package com.predic8.membrane.core.util.wsdl.parser;

import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.wsdl.parser.Binding.*;

public class WSDLParserContext {

    private Definitions definitions;
    private Resolver resolver;
    private String basePath;
    private Style style;

    public WSDLParserContext(Resolver resolver, String basePath) {
        this.basePath = basePath;
        this.resolver = resolver;
    }

    public WSDLParserContext(Definitions wsdl, Resolver resolver, String basePath) {
        this.definitions = wsdl;
        this.resolver = resolver;
        this.basePath = basePath;
    }

    public WSDLParserContext definitions(Definitions definitions) {
        this.definitions = definitions;
        return this;
    }

    public WSDLParserContext basePath(String basePath) {
        this.basePath = basePath;
        return this;
    }

    public WSDLParserContext resolver(Resolver resolver) {
        this.resolver = resolver;
        return this;
    }

    public WSDLParserContext style(Style style) {
        this.style = style;
        return this;
    }

    public Style getStyle() {
        return style;
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
