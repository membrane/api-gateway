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
