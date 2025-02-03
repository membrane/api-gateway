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
package com.predic8.membrane.core.interceptor;

import com.tngtech.archunit.core.domain.*;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

import java.util.List;
import java.util.stream.Collectors;

@AnalyzeClasses(packages = "com.predic8.membrane.core")
public class ThrowablePrintStackTraceTest {
    @ArchTest
    static final ArchRule throwablePrintStackTrace = ArchRuleDefinition.noMethods().that()
            .doNotHaveName("abrakadabra")
            .should(new CallsMethod(Throwable.class, "printStackTrace", new Class[0]))
            .as("You should not call throwable.printStackTrace() but log.error(\"\", throwable) .")
            .because("throwable.printStackTrace() prints the exception to System.stderr . This does not get picked up and is not configurable by logging.");

    static class CallsMethod extends ArchCondition<JavaMethod> {
        private final Class<?> clazz;
        private final String methodName;
        private final Class[] parameterClasses;
        private final String paramStr;

        CallsMethod(Class<?> clazz, String methodName, Class[] parameterClasses) {
            super("calls " + clazz.getName() + "." + methodName + "(..)");
            this.clazz = clazz;
            this.methodName = methodName;
            this.parameterClasses = parameterClasses;
            this.paramStr = parameterClasses.length > 0 ? ".." : "";
        }

        @Override
        public void check(JavaMethod inputMethod, ConditionEvents events) {
            List<JavaCall<?>> calls =
                    inputMethod.getMethodCallsFromSelf().stream()
                            .filter(mc -> mc.getTarget().getOwner().getFullName().equals(clazz.getName()))
                            .filter(mc -> mc.getTarget().getName().equals(methodName))
                            .filter(mc -> {
                                if (mc.getTarget().getRawParameterTypes().size() != parameterClasses.length)
                                    return false;
                                return true;
                            })
                            .collect(Collectors.toUnmodifiableList());

            if (calls.isEmpty()) {
                events.add(new SimpleConditionEvent(inputMethod, false, String.format("%s does not call %s.%s(%s)",
                        inputMethod.getDescription(), clazz.getName(), methodName, paramStr)));
                return;
            }
            for (JavaCall<?> call : calls) {
                String message = String.format("%s from %s calls %s.%s(%s)",
                        inputMethod.getDescription(), call.getSourceCodeLocation(), clazz.getName(), methodName, paramStr);
                events.add(new SimpleConditionEvent(inputMethod, true, message));
            }
        }
    }

}
