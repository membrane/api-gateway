/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.*;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.NAME;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public class FragmentDefinition extends ExecutableDefinition {
    String name;
    Type condition;
    List<Directive> directives;
    List<Selection> selections;

    public FragmentDefinition() {
    }

    public FragmentDefinition(String name, Type condition, List<Directive> directives, List<Selection> selections) {
        this.name = name;
        this.condition = condition;
        this.directives = directives;
        this.selections = selections;
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        name = parseName(tokenizer);
        if ("on".equals(name))
            throw new ParsingException("Fragment name may not be 'on'.", tokenizer.position());

        tokenizer.mustAdvance();
        if (tokenizer.type() == NAME && tokenizer.string().equals("on")) {
            tokenizer.mustAdvance();
            condition = parseType(tokenizer);
            tokenizer.mustAdvance();
        }

        directives = parseDirectivesOpt(tokenizer);
        if (directives != null)
            tokenizer.mustAdvance();

        if (tokenizer.type() != PUNCTUATOR || tokenizer.punctuator() != '{')
            throw new ParsingException("Expected '{'.", tokenizer.position());

        selections = parseSelectionSetOpt(tokenizer); // selections will always be found, since we are on a '{'
        // no need to revert, since selections cannot be null
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FragmentDefinition that = (FragmentDefinition) o;
        return Objects.equals(name, that.name) && Objects.equals(condition, that.condition) && Objects.equals(directives, that.directives) && Objects.equals(selections, that.selections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, condition, directives, selections);
    }

    @Override
    public String toString() {
        return "FragmentDefinition{" +
                "name='" + name + '\'' +
                ", condition=" + condition +
                ", directives=" + directives +
                ", selections=" + selections +
                '}';
    }

    public String getName() {
        return name;
    }

    public List<Selection> getSelections() {
        return selections;
    }
}
