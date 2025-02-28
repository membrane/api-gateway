package com.predic8.membrane.core.interceptor.soap;

import com.predic8.schema.creator.*;

public class TestCreatorContext extends SchemaCreatorContext implements Cloneable {

    int indent = 0;

    // OAS Parser
    StringBuffer sb = new StringBuffer();

    @Override
    public Object clone() {
        TestCreatorContext ctx =  new TestCreatorContext();
        ctx.indent = indent;
        ctx.sb = sb;
        return ctx;
    }

    public void add(String s) {
        sb.append(" ".repeat(indent));
        sb.append(s);
        sb.append("\n");
    }

    public void indent() {
        indent += 2;
    }
}
