package com.predic8.membrane.core.lang.spel;

import org.springframework.expression.common.*;

public class DollarTemplateParserContext extends TemplateParserContext {

    public static final DollarTemplateParserContext DOLLAR_TEMPLATE_PARSER_CONTEXT = new DollarTemplateParserContext();

    private DollarTemplateParserContext() {
        super("${", "}");
    }

}
