package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@AnalyzeClasses(packages = "com.predic8.membrane.core")
public class BodyDoesntThrowIOExceptionTest {
    @ArchTest
    static final ArchRule bodyMethodsShouldNotThrowIOException =
            methods().that()
            .areDeclaredInClassesThat().areAssignableTo(AbstractBody.class).and()
            .arePublic().and()
            .areNotStatic()
            .should().notDeclareThrowableOfType(IOException.class)
            .as("Public instance methods of Body and its subclasses should not throw IOException.")
            .because("""
                    a) We need to distinguish between HTTP server and client.
                    
                    b) The body should handle errors itself, so that resources can be freed correctly (HTTP stream handling).
                    
                    c) Throw ReadingBodyException or WritingBodyException instead.
                    """);

    @ArchTest
    static final ArchRule readingbodyexceptionsMustBeStoredInObservedexception =
            methods()
                    .that().areDeclaredInClassesThat().areAssignableTo(AbstractBody.class)
                    .and(callReadingBodyExceptionConstructor())
                    .should(storeExceptionInField())
                    .as("""
                            Public instance methods of Body and its subclasses should immediately assign the 'observedException'
                            field to the new ReadingBodyException, whenever a ReadingBodyException is created and thrown:
                            'throw observedException = new ReadingBodyException(e);'
                            """);


    private static DescribedPredicate<JavaMethod> callReadingBodyExceptionConstructor() {
        return new DescribedPredicate<>("call ReadingBodyException(Throwable) constructor") {
            @Override
            public boolean test(JavaMethod method) {
                return method.getConstructorCallsFromSelf().stream()
                        .anyMatch(call ->
                                call.getTargetOwner().getFullName().equals(ReadingBodyException.class.getName()) &&
                                        call.getTarget().getRawParameterTypes().size() == 1
                        );
            }
        };
    }

    private static ArchCondition<JavaMethod> storeExceptionInField() {
        return new ArchCondition<>("only use the \"throw setObservedException(new ReadingBodyException(e));\" pattern when throwing an RBE.") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                // Get line numbers of all ReadingBodyException instantiations
                Set<Integer> constructorLines = method.getConstructorCallsFromSelf().stream()
                        .filter(call -> call.getTargetOwner().getFullName().equals(ReadingBodyException.class.getName()))
                        .map(JavaConstructorCall::getLineNumber)
                        .collect(Collectors.toSet());

                // Find all calls to the 'setObservedException' method
                Set<Integer> fieldWriteLines = method.getAccessesFromSelf().stream()
                        .filter(JavaMethodCall.class::isInstance)
                        .map(JavaMethodCall.class::cast)
                        .filter(access -> access.getTarget().getName().equals("setObservedException"))
                        .map(JavaMethodCall::getLineNumber)
                        .collect(Collectors.toSet());

                for (Integer line : constructorLines) {
                    if (!fieldWriteLines.contains(line)) {
                        String message = String.format("In %s, ReadingBodyException is created on line %d but not assigned to 'observedException' (\"throw setObservedException(new ReadingBodyException(e));\")",
                                method.getFullName(), line);
                        events.add(SimpleConditionEvent.violated(method, message));
                    }
                }
            }
        };
    }
}
