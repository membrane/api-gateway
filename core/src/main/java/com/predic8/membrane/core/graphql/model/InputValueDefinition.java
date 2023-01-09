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
import com.predic8.membrane.core.graphql.ParserUtil;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.*;
import static com.predic8.membrane.core.graphql.ParserUtil.parseDirectivesConstOpt;
import static com.predic8.membrane.core.graphql.ParserUtil.parseValueConst;

public class InputValueDefinition {
    String description;
    String name;
    Type type;
    Value defaultValue;
    private List<Directive> directives;

    public InputValueDefinition() {
    }

    public InputValueDefinition(String description, String name, Type type, Value defaultValue, List<Directive> directives) {
        this.description = description;
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.directives = directives;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InputValueDefinition that = (InputValueDefinition) o;
        return Objects.equals(description, that.description) && Objects.equals(name, that.name) && Objects.equals(type, that.type) && Objects.equals(defaultValue, that.defaultValue) && Objects.equals(directives, that.directives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, name, type, defaultValue, directives);
    }

    @Override
    public String toString() {
        return "InputValueDefinition{" +
                "description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", defaultValue=" + defaultValue +
                ", directives=" + directives +
                '}';
    }

    public void parse(Tokenizer t) throws IOException, ParsingException {
        if (t.type() == STRING_VALUE) {
            description = t.string();
            t.mustAdvance();
        }

        if (t.type() != NAME)
            throw new ParsingException("Expected name.", t.position());
        name = t.string();

        t.mustAdvance();
        if (t.type() != PUNCTUATOR || t.punctuator() != ':')
            throw new ParsingException("Expected ':'.", t.position());

        t.mustAdvance();
        type = ParserUtil.parseType(t);

        if (!t.advance())
            return;

        if (t.type() == PUNCTUATOR && t.punctuator() == '=') {
            t.mustAdvance();
            defaultValue = parseValueConst(t);

            if (!t.advance())
                return;
        }

        directives = parseDirectivesConstOpt(t);
        if (directives == null)
            t.revert();
    }
}
