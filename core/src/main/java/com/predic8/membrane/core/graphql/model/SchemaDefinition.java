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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.parseDirectivesConstOpt;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public class SchemaDefinition implements TypeSystemDefinition {
    private String description;
    private List<Directive> directives;
    private List<RootOperationTypeDefinition> rootOperationTypeDefinitions = new ArrayList<>();

    public SchemaDefinition() {
    }

    public SchemaDefinition(String description, List<Directive> directives, List<RootOperationTypeDefinition> rootOperationTypeDefinitions) {
        this.description = description;
        this.directives = directives;
        this.rootOperationTypeDefinitions = rootOperationTypeDefinitions;
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {
        tokenizer.mustAdvance();

        directives = parseDirectivesConstOpt(tokenizer);
        if (directives != null) {
            tokenizer.mustAdvance();
        }

        if (tokenizer.type() != PUNCTUATOR || tokenizer.integer() != '{')
            throw new ParsingException("Expected '{'.", tokenizer.position());

        parseRootOperationTypeDefinitions(tokenizer);
    }

    private void parseRootOperationTypeDefinitions(Tokenizer tokenizer) throws IOException, ParsingException {
        tokenizer.mustAdvance();
        while(true) {
            if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == '}')
                return;

            RootOperationTypeDefinition rod = new RootOperationTypeDefinition();
            rod.parse(tokenizer);
            rootOperationTypeDefinitions.add(rod);

            if (!tokenizer.advance())
                throw new ParsingException("Expected '}'.", tokenizer.position());
        }
    }


    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchemaDefinition that = (SchemaDefinition) o;
        return Objects.equals(description, that.description) && Objects.equals(directives, that.directives) && Objects.equals(rootOperationTypeDefinitions, that.rootOperationTypeDefinitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, directives, rootOperationTypeDefinitions);
    }

    @Override
    public String toString() {
        return "SchemaDefinition{" +
                "description='" + description + '\'' +
                ", directives=" + directives +
                ", rootOperationTypeDefinitions=" + rootOperationTypeDefinitions +
                '}';
    }
}
