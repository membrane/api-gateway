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

package com.predic8.membrane.core.interceptor.log;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@AnalyzeClasses(packages = "com.predic8.membrane.core.interceptor.log.access")
public class AccessLogCallTest {

    private static final String LOGGER = "org.slf4j.Logger";

    @ArchTest
    void only_one_logger_call(JavaClasses classes) {
        List<JavaMethodCall> loggerCalls = classes.stream()
                .flatMap(c -> c.getCodeUnits().stream())
                .flatMap(u -> u.getMethodCallsFromSelf().stream())
                .filter(call -> call.getTarget().getOwner().getName().equals(LOGGER))
                .toList();

        assertEquals(1, loggerCalls.size(),
                "Expected exactly one Logger call, but found:\n" + loggerCalls.stream()
                        .map(call -> call.getSourceCodeLocation() + " -> " + call.getTarget().getFullName())
                        .collect(Collectors.joining("\n")));
    }
}
