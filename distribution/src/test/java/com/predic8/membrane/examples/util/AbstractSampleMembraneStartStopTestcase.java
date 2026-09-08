/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractSampleMembraneStartStopTestcase extends DistributionExtractingTestcase {

    protected Process2 process;

    /**
     * Console output of the running gateway. Recreated with every gateway start.
     */
    protected BufferLogger logger;

    /**
     * Whether every {@code @Test} needs its own gateway process.
     * <p>
     * Defaults to <code>false</code>: one gateway is started per test class. Starting one per
     * test method costs ~800ms each and leaves a server side TIME_WAIT entry on the fixed
     * tutorial ports for every connection of the killed process, which on macOS accumulates
     * until new outbound connects draw a colliding 4-tuple and have their SYN dropped.
     * <p>
     * Override and return <code>true</code> for tests that assert on startup output, patch the
     * configuration before startup, or otherwise depend on a pristine gateway.
     */
    protected boolean restartForEachTest() {
        return false;
    }

    @BeforeAll
    void startMembraneForClass() throws Exception {
        if (!restartForEachTest())
            startMembrane();
    }

    @AfterAll
    void stopMembraneAfterClass() {
        if (!restartForEachTest())
            stopMembrane();
    }

    @BeforeEach
    void startMembraneForTest() throws Exception {
        if (restartForEachTest())
            startMembrane();
    }

    @AfterEach
    void stopMembraneAfterTest() {
        if (restartForEachTest())
            stopMembrane();
    }

    protected void startMembrane() throws Exception {
        logger = new BufferLogger();
        process = startServiceProxyScript(logger);
    }

    protected void stopMembrane() {
        if (process != null) {
            process.killScript();
            process = null;
        }
    }
}
