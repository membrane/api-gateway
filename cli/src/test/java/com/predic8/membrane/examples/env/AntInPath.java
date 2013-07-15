package com.predic8.membrane.examples.env;

import java.io.File;
import java.io.IOException;

import junit.framework.Assert;

import org.junit.Test;

import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.test.AssertUtils;

public class AntInPath {
	
	/**
	 * Please make sure that the Apache Ant executable can be found in the PATH.
	 */
	@Test
	public void checkThatAntExecutableIsAvailable() throws IOException, InterruptedException {
		BufferLogger antOutput = new BufferLogger();
		Process2 ant = new Process2.Builder().in(new File(".")).executable("ant -version").withWatcher(antOutput).start();
		Assert.assertEquals(0, ant.waitFor(20000));
		AssertUtils.assertContains("Apache Ant", antOutput.toString());
	}

}
