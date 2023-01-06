package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.parseDirectivesOpt;
import static com.predic8.membrane.core.graphql.ParserUtil.parseName;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.NAME;

public class FragmentSpread extends Selection {
    private String fragmentName;
    private List<Directive> directives;

    public FragmentSpread() {
    }

    public FragmentSpread(String fragmentName, List<Directive> directives) {
        this.fragmentName = fragmentName;
        this.directives = directives;
    }

    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        if (tokenizer.type() != NAME)
            throw new ParsingException("Expected name.", tokenizer.position());

        fragmentName = tokenizer.string();
        if ("on".equals(fragmentName))
            throw new ParsingException("FragmentName may not be 'on'.", tokenizer.position());

        if (!tokenizer.advance())
            return;

        directives = parseDirectivesOpt(tokenizer);
        if (directives == null)
            tokenizer.revert();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FragmentSpread that = (FragmentSpread) o;
        return Objects.equals(fragmentName, that.fragmentName) && Objects.equals(directives, that.directives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fragmentName, directives);
    }

    @Override
    public String toString() {
        return "FragmentSpread{" +
                "fragmentName='" + fragmentName + '\'' +
                ", directives=" + directives +
                '}';
    }

    public String getFragmentName() {
        return fragmentName;
    }
}
