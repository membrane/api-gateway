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

package com.predic8.membrane.annot.yaml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurationParsingExceptionTest {

    /**
     * Many throw sites raise a {@link ConfigurationParsingException} without a
     * {@link ParsingContext} (e.g. {@code SpelEvaluator}, {@code ObjectBinder}). Rendering the
     * report must then return an empty string rather than throwing an NPE that would mask the
     * real error message (see #3041).
     */
    @Test
    void formattedReportWithoutParsingContextIsEmpty() {
        var e = new ConfigurationParsingException("Invalid SpEL expression: boom");
        assertEquals("", assertDoesNotThrow(e::getFormattedReport));
    }

    @Test
    void formattedReportWithoutParsingContextIsEmptyForCauseConstructor() {
        var e = new ConfigurationParsingException("Could not read file", new RuntimeException("io"), null);
        assertEquals("", assertDoesNotThrow(e::getFormattedReport));
    }
}
