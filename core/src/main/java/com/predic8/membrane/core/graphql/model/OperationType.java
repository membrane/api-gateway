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

import java.security.InvalidParameterException;
import java.util.Objects;

public class OperationType {
    private String operation;

    public OperationType() {
    }

    public OperationType(String operation) {
        this.operation = operation;
        if (!is(operation))
            throw new InvalidParameterException();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationType that = (OperationType) o;
        return Objects.equals(operation, that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operation);
    }

    @Override
    public String toString() {
        return "OperationType{" +
                "operation='" + operation + '\'' +
                '}';
    }

    public void parse(Tokenizer tokenizer) throws ParsingException {
        operation = tokenizer.string();
        if (!is(operation))
            throw new ParsingException("Invalid OperationType.", tokenizer.position());
    }

    public static boolean is(String operation) {
        return operation.equals("query") || operation.equals("mutation") || operation.equals("subscription");
    }

    public String getOperation() {
        return operation;
    }
}
