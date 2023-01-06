package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.STRING_VALUE;

public abstract class Type {
    public abstract void parse(Tokenizer tokenizer) throws IOException, ParsingException;
}
