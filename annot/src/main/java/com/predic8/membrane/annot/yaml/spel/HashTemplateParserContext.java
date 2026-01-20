package com.predic8.membrane.annot.yaml.spel;

import org.springframework.expression.common.TemplateParserContext;

public class HashTemplateParserContext extends TemplateParserContext {

    public static final HashTemplateParserContext DOLLAR_TEMPLATE_PARSER_CONTEXT = new HashTemplateParserContext();

    private HashTemplateParserContext() {
        super("#{", "}");
    }

}
