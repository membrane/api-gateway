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
import static com.predic8.membrane.core.graphql.ParserUtil.parseName;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public class EnumTypeDefinition implements TypeSystemDefinition {
    private String description;
    private String name;
    private List<Directive> directives;
    private List<EnumValueDefinition> enumValueDefinitions = new ArrayList<>();

    public EnumTypeDefinition() {
    }

    public EnumTypeDefinition(String description, String name, List<Directive> directives, List<EnumValueDefinition> enumValueDefinitions) {
        this.description = description;
        this.name = name;
        this.directives = directives;
        this.enumValueDefinitions = enumValueDefinitions;
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {

        name = parseName(tokenizer);

        if (!tokenizer.advance())
            return;

        directives = parseDirectivesConstOpt(tokenizer);
        if (directives != null) {
            if (!tokenizer.advance())
                return;
        }

        if (tokenizer.type() == PUNCTUATOR && tokenizer.integer() == '{') {
            parseEnumValuesDefinition(tokenizer);
            return;
        }

        tokenizer.revert();
    }

    private void parseEnumValuesDefinition(Tokenizer tokenizer) throws IOException, ParsingException {
        tokenizer.mustAdvance();
        while(true) {
            if (tokenizer.type() == PUNCTUATOR && tokenizer.punctuator() == '}')
                return;

            EnumValueDefinition evd = new EnumValueDefinition();
            evd.parse(tokenizer);
            enumValueDefinitions.add(evd);

            if (!tokenizer.advance())
                break;
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
        EnumTypeDefinition that = (EnumTypeDefinition) o;
        return Objects.equals(description, that.description) && Objects.equals(name, that.name) && Objects.equals(directives, that.directives) && Objects.equals(enumValueDefinitions, that.enumValueDefinitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, name, directives, enumValueDefinitions);
    }

    @Override
    public String toString() {
        return "EnumTypeDefinition{" +
                "description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", directives=" + directives +
                ", enumValueDefinitions=" + enumValueDefinitions +
                '}';
    }
}
