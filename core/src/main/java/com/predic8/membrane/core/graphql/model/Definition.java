package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;

public interface Definition {
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException;
}
