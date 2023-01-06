package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.NAME;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public abstract class Selection {
    public abstract void parse(Tokenizer tokenizer) throws IOException, ParsingException;

    public static Selection parseSelection(Tokenizer tokenizer) throws IOException, ParsingException {
        Selection sel = null;
        if (tokenizer.type() == NAME) {
            sel = new Field();
        } else if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == '.') {
            tokenizer.mustAdvance();
            if (tokenizer.type() == NAME && !tokenizer.string().equals("on"))
                sel = new FragmentSpread();
            else
                sel = new InlineFragment();
        }
        if (sel == null)
            throw new ParsingException("Expected field (name) or fragment ('...').", tokenizer.position());
        sel.parse(tokenizer);
        return sel;
    }
}
