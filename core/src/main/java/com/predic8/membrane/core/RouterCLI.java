/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core;


public class RouterCLI {

	public static void main(String[] args) {

		MembraneCommandLine cl = new MembraneCommandLine();
		try {
			cl.parse(args);
			if (cl.needHelp()) {
				cl.printUsage();
				return;
			}

			Router.init(getRulesFile(cl), RouterCLI.class.getClassLoader());
		} catch (Exception ex) {
			ex.printStackTrace();
			System.err.println("Could not read rules configuration. Please specify a file containing rules using the -c command line option. Or make sure that the file " + System.getenv("MEMBRANE_HOME") + "/conf/proxies.xml exists");
			System.exit(1);
		}

		new RouterCLI().waitForever();
		
	}

	private synchronized void waitForever() {
		try {
			wait();
		} catch (InterruptedException e) {
			// do nothing
		}
	}
	
	private static String getRulesFile(MembraneCommandLine line) {
		if (line.hasConfiguration()) {
			return line.getConfiguration();
		} else {
			return System.getenv("MEMBRANE_HOME") +  System.getProperty("file.separator") + "conf" + System.getProperty("file.separator") + "proxies.xml";
		}
	}

}
