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
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.parseValueQuestionConst;

public class Argument {
    String name;
    Value value;

    public Argument() {
    }

    public Argument(String name, Value value) {
        this.name = name;
        this.value = value;
    }

    public void parse(Tokenizer t) throws IOException, ParsingException {
        name = t.string();
        t.mustAdvance();
        if (t.type() != Tokenizer.Type.PUNCTUATOR || t.punctuator() != ':')
            throw new ParsingException("Expected ':'.", t.position());
        t.mustAdvance();
        value = parseValueQuestionConst(t);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Argument argument = (Argument) o;
        return Objects.equals(name, argument.name) && Objects.equals(value, argument.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        return "Argument{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}
