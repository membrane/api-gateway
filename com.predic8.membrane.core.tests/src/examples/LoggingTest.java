package examples;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.Test;

/**
 * Read {@link ScriptLauncher}.
 */
public class LoggingTest extends ScriptLauncher {

	public LoggingTest() {
		super("logging");
	}

	@Test
	public void test() throws IOException, InterruptedException {
		Process router = startScript("router");
		
		final boolean[] success = new boolean[1];
		
		addConsoleWatcher(router, new AbstractConsoleWatcher() {
			public void outputLine(boolean error, String line) {
				if (line.contains("HTTP"))
					success[0] = true;
			}
		});
		
		HttpClient hc = new HttpClient();
		assertEquals(hc.executeMethod(new GetMethod("http://localhost:2000/")), 200);
		
		assertEquals(true, success[0]);
		
		killScript(router);
	}

}
