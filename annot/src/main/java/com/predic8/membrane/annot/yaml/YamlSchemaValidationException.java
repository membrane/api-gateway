/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.yaml;

import com.networknt.schema.Error;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class YamlSchemaValidationException extends Exception {
    private final List<Error> errors;

    public YamlSchemaValidationException(String message, List<Error> errors) {
        super(message);
        this.errors = shortenErrorList(errors);
    }

    public List<Error> getErrors() {
        return errors;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " " + errors;
    }

    /**
     * Reduces oneOf groups (see {@link #reduceOneOfGroup(List)} and reduces additionalProperties groups
     * (see {@link #reduceAdditionalPropertiesGroups(List)}).
     */
    static List<Error> shortenErrorList(List<Error> errors) {
        return reduceOneOfGroup(reduceAdditionalPropertiesGroups(errors));
    }

    /**
     * If the evaluationPath of the Error ends with "/12/additionalProperties", this method returns the number '12'.
     * (or any other number).
     * @return the number 12, or -1 if the second last path part cannot be parsed as an integer.
     */
    static Integer extractNumberBeforeAdditionalProperties(Error error) {
        String path = error.getEvaluationPath().toString();
        if (!path.endsWith("/additionalProperties")) return null;
        try {
            return Integer.parseInt(getSecondLastPathPart(path));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String getSecondLastPathPart(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 2];
    }

    /**
     * If the evaluationPath of the Error ends with "/xy/additionalProperties" (or any other string instead of 'xy'),
     * this method strips off the two last path parts and returns the (rest of) the evaluation path. Elsewise the pure
     * evaluation path is returned.
     */
    static String getBasePath(Error error) {
        String path = error.getEvaluationPath().toString();
        if (!path.endsWith("/additionalProperties")) return path;
        int secondLastSlashIndex = path.lastIndexOf('/', path.length() - "/additionalProperties".length() - 1);
        return secondLastSlashIndex == -1 ? path : path.substring(0, secondLastSlashIndex);
    }

    /**
     * 'api: { foo: {} }' results in 387 errors. All but one of them have an evaluationPath starting with '/oneOf'.
     * This method removes all Errors except for the ones without '/oneOf'.
     */
    static List<Error> reduceOneOfGroup(List<Error> errors) {
        List<Error> errorsWithoutOneOf = errors.stream().filter(error -> !error.getEvaluationPath().toString().startsWith("/oneOf")).toList();
        return !errorsWithoutOneOf.isEmpty() ? errorsWithoutOneOf : errors;
    }

    /**
     * 'api: { flow: [ { return: {}, log: {} } ] }' results in 761 errors. The first 188 of them have an evaluationPath
     * with '/properties/api/$ref/properties/flow/$ref/items/anyOf/$i/additionalProperties' with $i ranging from 0 to
     * 94, two numbers occurring only once. This method removes all Errors except for the ones with the lowest frequency
     * of $i .
     */
    static List<Error> reduceAdditionalPropertiesGroups(List<Error> errors) {
        return groupByBasePath(errors)
                .map(group -> {
                    Map<Integer, Long> numberFrequency = collectFrequenciesOfNumberBeforeAdditionalProperties(group);
                    if (numberFrequency.isEmpty()) return group;
                    return filterByNumberBeforeAdditionalProperties(group, getKeysWithLowestValues(numberFrequency));
                })
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Removes all Errors from the given group that have a number before '/additionalProperties' that is not in the given set.
     * Example:
     *   Input: group:
     *      * /abc
     *      * /1/additionalProperties,
     *      * /2/additionalProperties,
     *      * /2/additionalProperties,
     *      * /$ref/additionalProperties
     *     rareNumbers: -1, 1
     *   Output: /abc, /1/additionalProperties, /$ref/additionalProperties
     */
    static List<Error> filterByNumberBeforeAdditionalProperties(List<Error> group, Set<Integer> rareNumbers) {
        return group.stream()
                .filter(error -> {
                    Integer num = extractNumberBeforeAdditionalProperties(error);
                    return num == null || rareNumbers.contains(num);
                })
                .toList();
    }

    /**
     * @return the keys of the given map with the lowest value.
     */
    static @NotNull Set<Integer> getKeysWithLowestValues(Map<Integer, Long> numberFrequency) {
        Long minFrequency = numberFrequency.values().stream().min(Long::compareTo).orElse(0L);
        return numberFrequency.entrySet().stream()
                .filter(e -> e.getValue().equals(minFrequency))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Counts how many times the number before '/additionalProperties' occurs in the given group.
     * Example:
     * /1/additionalProperties,
     * /2/additionalProperties,
     * /2/additionalProperties,
     * /$ref/additionalProperties
     *
     * Number 1 occours once, 2 occours twice and '$ref' (number -1) occurs also once.
     * @return a map from number to frequency
     */
    static @NotNull Map<Integer, Long> collectFrequenciesOfNumberBeforeAdditionalProperties(List<Error> group) {
        return group.stream()
                .map(YamlSchemaValidationException::extractNumberBeforeAdditionalProperties)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(i -> i, Collectors.counting()));
    }

    /**
     * Groups Errors by their base path (see {@link #getBasePath(Error)}).
     *
     * Example:
     * /1/additionalProperties -> basePath '/' -> group 1,
     * /2/additionalProperties -> basePath '/' -> group 1,
     * /bar/2/additionalProperties -> basePath '/bar' -> group 2,
     * /bar/$ref/additionalProperties -> basePath '/bar' -> group 2
     */
    static Stream<List<Error>> groupByBasePath(List<Error> errors) {
        return errors.stream()
                .collect(Collectors.groupingBy(YamlSchemaValidationException::getBasePath,
                        LinkedHashMap::new,
                        Collectors.toList())).values().stream();
    }
}
