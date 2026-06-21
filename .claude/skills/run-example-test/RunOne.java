/* Minimal JUnit Platform launcher used by the run-example-test skill to run a
   single distribution example/tutorial IT class, bypassing the hardcoded
   ExampleTests @Suite (which would otherwise pull in the whole ~6 min suite). */
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

public class RunOne {
    public static void main(String[] args) {
        LauncherDiscoveryRequest req = request()
                .selectors(selectClass(args[0]))
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(req);
        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out));
        summary.printFailuresTo(new PrintWriter(System.out));
        if (summary.getTotalFailureCount() > 0 || summary.getTestsFoundCount() == 0) {
            System.exit(1);
        }
    }
}
