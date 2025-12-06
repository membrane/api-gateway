/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import java.util.*;
import java.util.stream.Collectors;

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
     * @throws RuntimeException if the second last path part cannot be parsed as an integer.
     */
    static Integer extractNumberBeforeAdditionalProperties(Error error) {
        String path = error.getEvaluationPath().toString();
        if (!path.endsWith("/additionalProperties")) return null;
        String part = getSecondLastPathPart(path);
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getSecondLastPathPart(String path) {
        String[] parts = path.split("/");
        String part = parts[parts.length - 2];
        return part;
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
        //
        return errors.stream()
                // step 1: group by basePath
                .collect(Collectors.groupingBy(YamlSchemaValidationException::getBasePath,
                        LinkedHashMap::new,
                        Collectors.toList())).values().stream()
                // step 2: handle groups
                .map(group -> {
                    // step 2a: collect numberBeforeAdditionalProperties frequencies
                    Map<Integer, Long> numberFrequency = group.stream()
                            .map(YamlSchemaValidationException::extractNumberBeforeAdditionalProperties)
                            .filter(Objects::nonNull)
                            .collect(Collectors.groupingBy(i -> i, Collectors.counting()));

                    if (numberFrequency.isEmpty()) return group;

                    // step 2b: compute "rare" numbers (=with lowest frequency)
                    Long minFrequency = numberFrequency.values().stream().min(Long::compareTo).orElse(0L);
                    Set<Integer> rareNumbers = numberFrequency.entrySet().stream()
                            .filter(e -> e.getValue().equals(minFrequency))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toSet());

                    // step 2c: only return Errors with rare numbers
                    return group.stream()
                            .filter(error -> {
                                Integer num = extractNumberBeforeAdditionalProperties(error);
                                return num == null || rareNumbers.contains(num);
                            })
                            .toList();
                })
                .flatMap(List::stream)
                .toList();
    }
}
