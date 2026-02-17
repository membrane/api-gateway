package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.http.AbstractBody;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

import java.io.IOException;

@AnalyzeClasses(packages = "com.predic8.membrane.core")
public class BodyDoesntThrowIOExceptionTest {
    @ArchTest
    static final ArchRule bodyMethodsShouldNotThrowIOException =
            ArchRuleDefinition.methods().that()
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

}
