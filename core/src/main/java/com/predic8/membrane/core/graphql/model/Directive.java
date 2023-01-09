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

import static com.predic8.membrane.core.graphql.ParserUtil.parseName;
import static com.predic8.membrane.core.graphql.ParserUtil.parseOptionalArguments;

public class Directive {
    private String name;
    private List<Argument> arguments;

    public Directive() {
    }

    public Directive(String name) {
        this.name = name;
    }

    public Directive(String name, List<Argument> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public void parse(Tokenizer t) throws IOException, ParsingException {
        name = parseName(t);
        if (t.advance()) {
            arguments = parseOptionalArguments(t);
            if (arguments == null)
                t.revert();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Directive directive = (Directive) o;
        return Objects.equals(name, directive.name) && Objects.equals(arguments, directive.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, arguments);
    }

    @Override
    public String toString() {
        return "Directive{" +
                "name='" + name + '\'' +
                ", arguments=" + arguments +
                '}';
    }
}