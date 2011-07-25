/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import org.apache.commons.cli.ParseException;

import com.predic8.membrane.core.transport.PortOccupiedException;

public class RouterCLI {

	public static void main(String[] args) throws ParseException {

		MembraneCommandLine cl = new MembraneCommandLine();
		cl.parse(args);
		if (cl.needHelp()) {
			cl.printUsage();
			return;
		}

		try {
			Router.init(getConfigFile(cl), RouterCLI.class.getClassLoader()).getConfigurationManager().loadConfiguration(getRulesFile(cl));
		} catch (ClassNotFoundException e) {
		
			e.printStackTrace();
			
		} catch (PortOccupiedException e) { 
			System.err.println(e.getMessage());
			System.exit(1);
		} catch (Exception ex) {
			ex.printStackTrace();
			System.err.println("Could not read rules configuration. Please specify a file containing rules using the -c command line option. Or make sure that the file " + System.getenv("MEMBRANE_HOME") + "/conf/rules.xml exists");
			System.exit(1);
		}

		
		new RouterCLI().waitForever();
		
	}

	private synchronized void waitForever() {
		try {
			wait();
		} catch (InterruptedException e) {
			
		}
	}
	
	private static String getRulesFile(MembraneCommandLine line) {
		if (line.hasConfiguration()) {
			return line.getConfiguration();
		} else {
			return System.getenv("MEMBRANE_HOME") +  System.getProperty("file.separator") + "conf" + System.getProperty("file.separator") + "rules.xml";
		}
	}

	private static String getConfigFile(MembraneCommandLine line) {
		if (line.hasMonitorBeans()) {
			return "file:" + line.getMonitorBeans();
		}
		return System.getenv("MEMBRANE_HOME") +  System.getProperty("file.separator") + "conf"  + System.getProperty("file.separator") + "monitor-beans.xml";
	}

}
