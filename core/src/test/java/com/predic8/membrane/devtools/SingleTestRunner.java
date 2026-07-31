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

package com.predic8.membrane.devtools;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

/**
 * Runs a single test class or package via the JUnit Platform Launcher, bypassing the
 * {@code UnitTests} suite that Surefire is bound to in this module (see
 * {@code test/scripts/run-core-test.sh}, which invokes this class).
 * Motivation: Speed up test execution in claude code
 */
public class SingleTestRunner {
    public static void main(String[] args) throws Exception {
        boolean isPackage = "package".equals(args[1]);
        LauncherDiscoveryRequest discoveryRequest = request()
                .selectors(isPackage ? selectPackage(args[0]) : selectClass(args[0]))
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(discoveryRequest);

        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out));
        summary.printFailuresTo(new PrintWriter(System.out), 100);
        System.exit(summary.getTotalFailureCount() == 0 ? 0 : 1);
    }
}
