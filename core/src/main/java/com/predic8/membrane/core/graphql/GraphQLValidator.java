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

package com.predic8.membrane.core.graphql;

import com.predic8.membrane.core.graphql.model.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * So far, this is not a complete validator.
 */
public class GraphQLValidator {
    public List<String> validate(ExecutableDocument ed) {
        List<String> res = new ArrayList<>();
        if (!ed.getExecutableDefinitions().stream()
                .filter(ed2 -> ed2 instanceof OperationDefinition)
                .map(ed2 -> ((OperationDefinition)ed2).getName())
                .filter(Objects::nonNull)
                .allMatch(new HashSet<>()::add))
            res.add("Names of operationDefinitions must be unique.");

        if (ed.getExecutableDefinitions().stream()
                .filter(ed2 -> ed2 instanceof OperationDefinition)
                .map(ed2 -> ((OperationDefinition)ed2).getName())
                .anyMatch(Objects::isNull) &&
            ed.getExecutableDefinitions().stream()
                    .filter(ed2 -> ed2 instanceof OperationDefinition)
                    .count() > 1)
            res.add("If there is an anonymous operationDefinition, there may not be other operationDefinitions.");

        if (!ed.getExecutableDefinitions().stream()
                .filter(ed2 -> ed2 instanceof FragmentDefinition)
                .map(ed2 -> ((FragmentDefinition)ed2).getName())
                .filter(Objects::nonNull)
                .allMatch(new HashSet<>()::add))
            res.add("Names of fragmentDefinitions must be unique.");

        return res;
    }
}
