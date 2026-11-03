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

import com.predic8.membrane.core.graphql.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.*;

public class ExecutableDocument {

    /**
     * Maximum number of named fragments spread inside each other. Traversals that expand fragments recurse once per
     * level, so an unbounded chain of fragments could overflow the thread stack.
     */
    public static final int MAX_FRAGMENT_NESTING = 50;

    public static final String FRAGMENTS_NESTED_TOO_DEEPLY = "Fragments are nested too deeply.";

    final List<ExecutableDefinition> executableDefinitions = new ArrayList<>();

    /**
     * Index of {@link #executableDefinitions} for fragment lookups during traversal. Holds the first definition of
     * each name.
     */
    private final Map<String, FragmentDefinition> fragmentsByName = new HashMap<>();

    public ExecutableDocument() {
    }

    public ExecutableDocument(ExecutableDefinition... executableDefinitions) {
        for (final var definition : executableDefinitions)
            add(definition);
    }

    private void add(ExecutableDefinition definition) {
        executableDefinitions.add(definition);
        if (definition instanceof FragmentDefinition fd)
            fragmentsByName.putIfAbsent(fd.getName(), fd);
    }

    /**
     * Searches for multiple OperationDefinitions with the same name. Is needed by the validator
     * @param name
     * @return
     */
    public @NotNull List<OperationDefinition> getOperationDefinitionsByName(Object name) {
        return getOperationDefinitions().stream()
                .filter(od -> name.equals(od.getName())).toList();
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
                    add(od);
                    continue;
                }
                if (t.string().equals("fragment")) {
                    FragmentDefinition fd = new FragmentDefinition();
                    fd.parse(t);
                    add(fd);
                    continue;
                }
            }

            if (executableDefinitions.isEmpty() && !shortHandQuery) {
                OperationDefinition od = new OperationDefinition();
                od.parse(t);
                add(od);
                shortHandQuery = true;
                continue;
            }

            throw new ParsingException("Expected 'query', 'mutation', 'subscription' or 'fragment'.", t.position());
        }
        if (shortHandQuery && executableDefinitions.size() != 1)
            throw new ParsingException("The 'query' keyword may only be omitted, if there is exactly one query present"+
                    " in the ExecutableDocument.", t.position());
        List<OperationDefinition> operations = getOperationDefinitions();

        if (operations.size() > 1)
            for (OperationDefinition op : operations) {
                if (op.getName() == null)
                    throw new ParsingException("Multiple queries must all be named.", t.position());
            }
    }

    public @NotNull List<OperationDefinition> getOperationDefinitions() {
        return executableDefinitions.stream()
                .filter(ed -> ed instanceof OperationDefinition)
                .map(ed -> (OperationDefinition) ed)
                .toList();
    }

    public @NotNull List<FragmentDefinition> getFragmentDefinitions() {
        return executableDefinitions.stream()
                .filter(ed -> ed instanceof FragmentDefinition)
                .map(ed -> (FragmentDefinition) ed)
                .toList();
    }

    public Optional<FragmentDefinition> getFragmentDefinition(String name) {
        return Optional.ofNullable(fragmentsByName.get(name));
    }

    /**
     * Returns the fields that are executed for a selection set, following the GraphQL algorithm
     * <a href="https://spec.graphql.org/October2021/#CollectFields()">CollectFields</a>: inline fragments and named
     * fragment spreads are expanded in place, the sub-selections of fields are not entered.
     * <p>
     * Like in the specification, a named fragment is expanded only once per call. This keeps the result linear in
     * the size of the document even for cyclic or repeatedly spread fragments.
     * </p>
     *
     * @throws GraphQLOverHttpValidationException if a spread references a fragment that is not defined, or named
     *                                            fragments are nested deeper than {@link #MAX_FRAGMENT_NESTING}
     */
    public List<Field> collectFields(List<Selection> selections) {
        final var fields = new ArrayList<Field>();
        collectFields(selections, new HashSet<>(), fields, 0);
        return fields;
    }

    private void collectFields(List<Selection> selections, Set<String> visitedFragments, List<Field> fields, int fragmentNesting) {
        if (selections == null)
            return;
        for (final var selection : selections) {
            if (selection instanceof Field field) {
                fields.add(field);
            } else if (selection instanceof InlineFragment inlineFragment) {
                collectFields(inlineFragment.getSelections(), visitedFragments, fields, fragmentNesting);
            } else if (selection instanceof FragmentSpread spread && visitedFragments.add(spread.getFragmentName())) {
                if (fragmentNesting >= MAX_FRAGMENT_NESTING)
                    throw new GraphQLOverHttpValidationException(FRAGMENTS_NESTED_TOO_DEEPLY);
                collectFields(getFragmentDefinition(spread.getFragmentName())
                        .orElseThrow(() -> new GraphQLOverHttpValidationException("Did not find fragment '%s'.".formatted(spread.getFragmentName())))
                        .getSelections(), visitedFragments, fields, fragmentNesting + 1);
            }
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
