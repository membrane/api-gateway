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
