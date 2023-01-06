package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.NAME;

public abstract class DirectiveLocation {
    public static DirectiveLocation parseDirectiveLocation(Tokenizer tokenizer) throws ParsingException {
        if (tokenizer.type() != NAME)
            throw new ParsingException("Expected name.", tokenizer.position());
        String s = tokenizer.string();
        if (ExecutableDirectiveLocation.is(s))
            return new ExecutableDirectiveLocation(s);
        if (TypeSystemDirectiveLocation.is(s))
            return new TypeSystemDirectiveLocation(s);
        throw new ParsingException("Not a valid DirectiveLocation.", tokenizer.position());
    }
}
