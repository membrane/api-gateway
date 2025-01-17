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
