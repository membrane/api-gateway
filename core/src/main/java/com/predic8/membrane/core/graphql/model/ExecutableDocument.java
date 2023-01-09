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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.NAME;

public class ExecutableDocument {
    List<ExecutableDefinition> executableDefinitions = new ArrayList<>();

    public ExecutableDocument() {
    }

    public ExecutableDocument(ExecutableDefinition... executableDefinitions) {
        this.executableDefinitions.addAll(Arrays.asList(executableDefinitions));
    }

    public void parse(Tokenizer t) throws IOException, ParsingException {
        boolean shortHandQuery = false;
        while (true) {
            if (!t.advance())
                break;

            if (t.type() == NAME) {
                if (t.string().equals("query") || t.string().equals("mutation") || t.string().equals("subscription")) {
                    OperationDefinition od = new OperationDefinition();
                    od.parse(t);
                    executableDefinitions.add(od);
                    continue;
                }
                if (t.string().equals("fragment")) {
                    FragmentDefinition fd = new FragmentDefinition();
                    fd.parse(t);
                    executableDefinitions.add(fd);
                    continue;
                }
            }

            if (executableDefinitions.size() == 0 && !shortHandQuery) {
                OperationDefinition od = new OperationDefinition();
                od.parse(t);
                executableDefinitions.add(od);
                shortHandQuery = true;
                continue;
            }

            throw new ParsingException("Expected 'query', 'mutation', 'subscription' or 'fragment'.", t.position());
        }
        if (shortHandQuery && executableDefinitions.size() != 1)
            throw new ParsingException("The 'query' keyword may only be omitted, if there is exactly one query present"+
                    " in the ExecutableDocument.", t.position());
        List<OperationDefinition> operations = executableDefinitions.stream()
                .filter(ed -> ed instanceof OperationDefinition)
                .map(ed -> (OperationDefinition)ed)
                .toList();
        if (operations.size() > 1)
            for (OperationDefinition op : operations) {
                if (op.getName() == null)
                    throw new ParsingException("Multiple queries must all be named.", t.position());
            }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutableDocument that = (ExecutableDocument) o;
        return Objects.equals(executableDefinitions, that.executableDefinitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executableDefinitions);
    }

    @Override
    public String toString() {
        return "ExecutableDocument{" +
                "executableDefinitions=" + executableDefinitions +
                '}';
    }

    public List<ExecutableDefinition> getExecutableDefinitions() {
        return executableDefinitions;
    }
}
