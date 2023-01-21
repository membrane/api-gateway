/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.examples.env;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.examples.util.BufferLogger;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AntInPath {

	/**
	 * Please make sure that the Apache Ant executable can be found in the PATH.
	 */
	@Test
	public void checkThatAntExecutableIsAvailable() throws IOException, InterruptedException {
		BufferLogger antOutput = new BufferLogger();
		Process2 ant = new Process2.Builder().in(new File(".")).executable("ant -version").withWatcher(antOutput).start();
		assertEquals(0, ant.waitFor(20000));
		assertContains("Apache Ant", antOutput.toString());
	}

}
