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

import static com.predic8.membrane.core.graphql.ParserUtil.parseDirectivesConstOpt;
import static com.predic8.membrane.core.graphql.ParserUtil.parseName;

public class ScalarTypeDefinition implements TypeSystemDefinition {
    private String description;
    private String name;
    private List<Directive> directives;

    public ScalarTypeDefinition() {
    }

    public ScalarTypeDefinition(String description, String name, List<Directive> directives) {
        this.description = description;
        this.name = name;
        this.directives = directives;
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {

        name = parseName(tokenizer);

        if (!tokenizer.advance())
            return;

        directives = parseDirectivesConstOpt(tokenizer);
        if (directives == null)
            tokenizer.revert();
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScalarTypeDefinition that = (ScalarTypeDefinition) o;
        return Objects.equals(description, that.description) && Objects.equals(name, that.name) && Objects.equals(directives, that.directives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, name, directives);
    }

    @Override
    public String toString() {
        return "ScalarTypeDefinition{" +
                "description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", directives=" + directives +
                '}';
    }
}
