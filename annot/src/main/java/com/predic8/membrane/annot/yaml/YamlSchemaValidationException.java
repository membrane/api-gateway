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

    private static Integer extractNumberBeforeAdditionalProperties(Error error) {
        String path = error.getEvaluationPath().toString();
        if (!path.endsWith("/additionalProperties")) return null;
        String[] parts = path.split("/");
        try {
            return Integer.parseInt(parts[parts.length - 2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String getBasePath(Error error) {
        String path = error.getEvaluationPath().toString();
        if (!path.endsWith("/additionalProperties")) return path;
        return path.substring(0, path.lastIndexOf('/', path.length() - "/additionalProperties".length()-1));
    }

    private static List<Error> shortenErrorList(List<Error> errors) {
        // api: { flow: [ { return: {}, log: {} } ] } results in 761 errors. the first 188 of them have an evaluationPath
        // with '/properties/api/$ref/properties/flow/$ref/items/anyOf/$i/additionalProperties'
        // with $i ranging from 0 to 94, two numbers occurring only once.
        // we remove all Errors except for the ones with the lowest frequency of $i
        Map<String, List<Error>> groupedErrors = errors.stream()
                .collect(Collectors.groupingBy(YamlSchemaValidationException::getBasePath,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<Error> filteredErrors = groupedErrors.values().stream()
                .map(group -> {
                    Map<Integer, Long> numberFrequency = group.stream()
                            .map(YamlSchemaValidationException::extractNumberBeforeAdditionalProperties)
                            .filter(Objects::nonNull)
                            .collect(Collectors.groupingBy(i -> i, Collectors.counting()));

                    if (numberFrequency.isEmpty()) return group;

                    Long minFrequency = numberFrequency.values().stream().min(Long::compareTo).orElse(0L);
                    Set<Integer> rareNumbers = numberFrequency.entrySet().stream()
                            .filter(e -> e.getValue().equals(minFrequency))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toSet());

                    return group.stream()
                            .filter(error -> {
                                Integer num = extractNumberBeforeAdditionalProperties(error);
                                return num == null || rareNumbers.contains(num);
                            })
                            .toList();
                })
                .flatMap(List::stream)
                .toList();

        // api: { foo: {} } results in 387 errors. all but one of them have an evaluationPath starting with '/oneOf'
        // we remove all Errors except for the ones without '/oneOf'
        List<Error> errorsWithoutOneOf = filteredErrors.stream().filter(error -> !error.getEvaluationPath().toString().startsWith("/oneOf")).toList();
        if (!errorsWithoutOneOf.isEmpty())
            filteredErrors = errorsWithoutOneOf;
        return filteredErrors;
    }
}
