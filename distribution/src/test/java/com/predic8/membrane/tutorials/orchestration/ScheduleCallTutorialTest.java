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

package com.predic8.membrane.tutorials.orchestration;

import com.google.common.base.Predicate;
import com.predic8.membrane.examples.util.WaitableConsoleEvent;
import org.junit.jupiter.api.Test;

public class ScheduleCallTutorialTest extends AbstractOrchestrationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "70-Schedule-Call.yaml";
    }

    @Test
    void productCountIsLogged() throws Exception {
        // The shop is writable, so the test does not check the exact number.
        final var count = new WaitableConsoleEvent(process, (Predicate<String>) line -> line.matches(".*Number of products: \\d+.*"));
        // The first call starts 10 seconds after startup.
        count.waitFor(20_000L);
    }
}
