/* Copyright 2012 predic8 GmbH, www.predic8.com

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
import java.util.Set;

@AnalyzeClasses(packages = "com.predic8.membrane.core")
public class InterceptorInitADRTest {
    @ArchTest
    static final ArchRule interceptor_init_adr = ArchRuleDefinition.methods().that()
            .haveName("init").and()
            .haveRawParameterTypes(new Class[0]).and()
            .areDeclaredInClassesThat().areAssignableTo(AbstractInterceptor.class).and()
            .areDeclaredInClassesThat().areNotAssignableFrom(AbstractInterceptor.class)
            .should(new CallSuperClassMethod())
            .as("Interceptor init() methods must call super.init()")
            .because("Not initializing your superclass might result in weird errors");

    static class CallSuperClassMethod extends ArchCondition<JavaMethod> {
        CallSuperClassMethod() {
            super("call super class method");
        }

        @Override
        public void check(JavaMethod inputMethod, ConditionEvents events) {
            String ownerFullName = inputMethod.getOwner().getFullName();
            List<JavaClass> inputParameters = inputMethod.getRawParameterTypes();
            Set<JavaMethodCall> callsFromSelf = inputMethod.getMethodCallsFromSelf();
            boolean satisfied = callsFromSelf.stream()
                    .map(JavaCall::getTarget)
                    .filter(target -> !ownerFullName.equals(target.getOwner().getFullName()))
                    .filter(target -> target.getName().equals(inputMethod.getName()))
                    .map(AccessTarget.CodeUnitCallTarget::getRawParameterTypes)
                    .anyMatch(targetTypes -> targetTypes.size() == inputParameters.size());
            String message = String.format("%s does not call super class method at %s",
                    inputMethod.getDescription(), inputMethod.getSourceCodeLocation());
            events.add(new SimpleConditionEvent(inputMethod, satisfied, message));
        }
    }
}
