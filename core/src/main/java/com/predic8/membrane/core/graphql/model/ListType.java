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
import java.util.Objects;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public class ListType extends Type {
    Type type;
    boolean nullable;

    public ListType() {
    }

    public ListType(Type type) {
        this.type = type;
    }

    public ListType(Type type, boolean nullable) {
        this.type = type;
        this.nullable = nullable;
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        tokenizer.mustAdvance();
        type = ParserUtil.parseType(tokenizer);

        tokenizer.mustAdvance();
        if (tokenizer.type() != PUNCTUATOR || tokenizer.punctuator() != ']')
            throw new ParsingException("Expected ']'.", tokenizer.position());

        if (!tokenizer.advance())
            return;

        if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == '!') {
            nullable = true;
        } else {
            tokenizer.revert();
        }

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ListType listType = (ListType) o;
        return nullable == listType.nullable && Objects.equals(type, listType.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, nullable);
    }

    @Override
    public String toString() {
        return "ListType{" +
                "type=" + type +
                ", nullable=" + nullable +
                '}';
    }
}
