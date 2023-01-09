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

import static com.predic8.membrane.core.graphql.Tokenizer.Type.*;

public class RootOperationTypeDefinition {
    private OperationType operationType;
    private NamedType type;

    public RootOperationTypeDefinition() {
    }

    public RootOperationTypeDefinition(OperationType operationType, NamedType type) {
        this.operationType = operationType;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RootOperationTypeDefinition that = (RootOperationTypeDefinition) o;
        return Objects.equals(operationType, that.operationType) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, type);
    }

    @Override
    public String toString() {
        return "RootOperationDefinition{" +
                "operationType=" + operationType +
                ", type=" + type +
                '}';
    }

    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        operationType = new OperationType();
        operationType.parse(tokenizer);

        tokenizer.mustAdvance();
        if (tokenizer.type() != PUNCTUATOR || tokenizer.punctuator() != ':')
            throw new ParsingException("Expected ':'.", tokenizer.position());

        tokenizer.mustAdvance();
        if (tokenizer.type() != NAME)
            throw new ParsingException("Expected type.", tokenizer.position());

        type = new NamedType();
        type.parse(tokenizer);
    }

}
