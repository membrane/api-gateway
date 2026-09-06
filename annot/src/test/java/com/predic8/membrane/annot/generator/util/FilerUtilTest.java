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
package com.predic8.membrane.annot.generator.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.processing.FilerException;

import static com.predic8.membrane.annot.generator.util.FilerUtil.isAlreadyCreated;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilerUtilTest {

    /**
     * The exact texts javac's JavacFiler produces when a file is created twice. Getting these wrong
     * turns the generators' tolerated "already generated in an earlier round" case into a build crash.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "Attempt to recreate a file for type com.example.FooParser",
            "Attempt to reopen a file for path /out/com/example/router-conf.xsd",
            "Source file already created: com.example.Foo"
    })
    void alreadyCreatedMessages(String message) {
        assertTrue(isAlreadyCreated(new FilerException(message)));
    }

    @Test
    void unrelatedFilerExceptionIsARealFailure() {
        assertFalse(isAlreadyCreated(new FilerException("Illegal name com.example.123")));
    }

    @Test
    void nullMessageIsARealFailure() {
        assertFalse(isAlreadyCreated(new FilerException(null)));
    }
}
