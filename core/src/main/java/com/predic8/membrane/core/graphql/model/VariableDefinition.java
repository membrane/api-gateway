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

import com.predic8.membrane.core.graphql.ParserUtil;
import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public class VariableDefinition {
    private Variable variable;
    private Type type;
    private Value defaultValue;
    private List<Directive> directives;

    public VariableDefinition() {
    }

    public VariableDefinition(Variable variable, Type type, Value defaultValue, List<Directive> directives) {
        this.variable = variable;
        this.type = type;
        this.defaultValue = defaultValue;
        this.directives = directives;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VariableDefinition that = (VariableDefinition) o;
        return Objects.equals(variable, that.variable) && Objects.equals(type, that.type) && Objects.equals(defaultValue, that.defaultValue) && Objects.equals(directives, that.directives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable, type, defaultValue, directives);
    }

    @Override
    public String toString() {
        return "VariableDefinition{" +
                "variable='" + variable + '\'' +
                ", type=" + type +
                ", defaultValue=" + defaultValue +
                ", directives=" + directives +
                '}';
    }

    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        variable = new Variable();
        variable.parse(tokenizer);

        tokenizer.mustAdvance();
        if (tokenizer.type() != PUNCTUATOR || tokenizer.punctuator() != ':')
            throw new ParsingException("Expected ':'.", tokenizer.position());

        tokenizer.mustAdvance();
        type = ParserUtil.parseType(tokenizer);

        if (!tokenizer.advance())
            return;

        if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == '=') {
            tokenizer.mustAdvance();
            defaultValue = ParserUtil.parseValueConst(tokenizer);
            if (!tokenizer.advance())
                return;
        }

        directives = ParserUtil.parseDirectivesConstOpt(tokenizer);
        if (directives == null)
            tokenizer.revert();
    }
}
